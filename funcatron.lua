local funcatron = {}

local random = require "random"

local cjson = require("cjson")

local rabbitmqstomp = require("resty/rabbitmqstomp")

local semaphore = require "ngx.semaphore"

-- generate a UUID
function funcatron.uuid()
   local template ='Fxxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
   return string.gsub(template, '[xy]', function (c)
                         local v = (c == 'x') and random.number(0, 0xf) or
                            random.number(8, 0xb)
                         return string.format('%x', v)
   end)
end

funcatron.random = random

local rabbitmqstomp = require("resty/rabbitmqstomp")

local http_port = os.getenv("PORT_80") or "80"

local http_host = os.getenv("HOST") or "localhost"

local bunny_host = os.getenv("RABBIT_HOST") or
   os.getenv("FUNC_RABBIT_PORT_61613_TCP_ADDR")

local bunny_port = (tonumber(os.getenv("RABBIT_PORT") or "61613")) or 61613

local bunny_user = os.getenv("RABBIT_USER") or "guest"

local bunny_pwd = os.getenv("RABBIT_PWD") or "guest"

local tron_queue = os.getenv("TRON_QUEUE") or "for_tron"

local dev_mode = os.getenv("DEV_MODE")

local nginx_uuid = os.getenv("INSTANCEID") or "no-id"

local keep_running = true

function funcatron.rabbit_connection()
   local rabbit, err = rabbitmqstomp:new()

   rabbit:set_timeout(10000)

   local ok, err =
      rabbit:connect({host=bunny_host,
                      port=bunny_port,
                      username=bunny_user,
                      password=bunny_pwd})

   if err then
      rabbit:close()
      return nil, err
   end

   return rabbit, nil

end

funcatron.instance_uuid = funcatron.uuid();


-- a table of response types to handlers
local response_handlers = {}


-- register with the Tron and listen
-- for messages
local function register_and_listen()

   if not keep_running then
      return
   end

   local rabbit, err = funcatron.rabbit_connection()

   if dev_mode then
      ngx.log(ngx.ALERT, "Dev mode: setting up listen mode")
   end


   if err then
      -- on connection failure, try again in 1/3 of second
      ngx.timer.at(0.3, register_and_listen)
      return
   end

   local msg_uuid = funcatron.uuid()

   -- {:action "awake"
   --     :type "frontend"
   --     :msg-id UUID-string
   --     :instance-id Unique-id
   --     :from UUID-String
   --     :at currentTimeMillis
   -- }

   local msg = cjson.encode({action="awake",
                             type="frontend",
                             ["instance-id"]=nginx_uuid,
                             from=funcatron.instance_uuid,
                             at=(os.time() * 1000),
                             host_info={host=http_host, port=http_port},
                             ["msg-id"]=msg_uuid})

   local headers = {["x-type"] = "awake",
      destination=("/amq/queue/" .. tron_queue),
      persistent="false",
      ["content-type"]="application/json",
      receipt=msg_uuid
   }

   local ok, err = rabbit:send(msg, headers)

   if err then
      rabbit:close()
      ngx.timer.at(0.3, register_and_listen)
      return
   end

   local ok, err = rabbit:subscribe({destination="/queue/" ..
                                        funcatron.instance_uuid,
                                     persistent="false",
                                     id=funcatron.instance_uuid})

   while keep_running or (dev_mode and next(funcatron.response_table) ~= nil) do
      if dev_mode then
         ngx.log(ngx.ALERT, "Dev mode: in listen loop " ..
                    funcatron.instance_uuid)
      end

      local data, err = rabbit:receive()

      if err then
         if err ~= "timeout" then -- ignore timeouts
            -- otherwise close the socket and re-register
            rabbit:close()
            ngx.timer.at(0.1, register_and_listen)
            return
         end
      else
         local response = cjson.decode(data)
         local the_func = response_handlers[response.action]
         if dev_mode then
            ngx.log(ngx.ALERT, "Dev mode: got action: " .. response.action)
         end

         if the_func then
            the_func(response)
         else
            ngx.log(ngx.ERR, "Failed to handle message action: "
                       .. response.action .. " message " ..
                       cjson.encode(response))
         end
      end
   end

   ngx.log(ngx.ALERT, "Gracefully stopping")
   rabbit:close()
end

-- delete elements from the array that
-- are more than 15 minutes old
local function clean_cruft_from(ary)
   local too_old = os.time() - (15 * 60)
   local to_remove = {}

   for k,v in ipairs(ary) do
      if v and v.when and type(v.when) == "number" and v.when < too_old then
         table.insert(to_remove, k)
      end
   end

   for k,v in ipairs(to_remove) do
      ary[k] = nil
   end
end

funcatron.response_table = {}

funcatron.routing_table = {}

response_handlers["route"] = function(msg)
   ngx.log(ngx.ALERT, "Deploying new route table... " .. cjson.encode(msg))
   funcatron.routing_table = msg.routes or {}
end

response_handlers["tron-info"] = function(msg)
   -- do nothing... we don't http to TRON
end

response_handlers["heartbeat"] = function(msg)
   ngx.log(ngx.TRACE, "Got heartbeat from " .. msg.from)
end

response_handlers["die"] = function(msg)
   keep_running = false
   ngx.log(ngx.ALERT, "Got the 'die' Command... winding down...")
end

response_handlers["answer"] = function(msg)
   local key = msg["request-id"]
   ngx.log(ngx.INFO, ("Got answer from " .. (msg.from or "Unknown") .. " for req " ..
                         key))
   local current = funcatron.response_table[key]
   funcatron.response_table[key] = {["type"]="answer",
      data=msg.answer,
      when=os.time()}

   clean_cruft_from(funcatron.response_table)

   -- if there's a thread or more waiting on the
   -- semaphore, wake them
   if current and current.type == "wait" then
      current.sema:post(99)
   end

end


local function string_starts(String,Start)
   return string.sub(String,1,string.len(Start))==Start
end

function funcatron.route_for(host, path)
      -- when the routing table is empty,
   -- wait a second assuming we're just starting
   -- up and we want to wait for the Tron registration
   if next(funcatron.routing_table) == nil then
      ngx.sleep(1)
   end

   -- find the entry that matches the host and path
   for i, v in ipairs(funcatron.routing_table) do
      if (dev_mode or (not v.host) or v.host == "*" or v.host == host) and
      string_starts(path, v.path) then
         return v.queue, nil
      end
   end

   return nil, ("No function defined to service host " .. host ..
                   " and path " .. path)
end

-- wait until a key is populated into the
-- response table or timeout trying
function funcatron.get_response(key, timeout)
   local answer = funcatron.response_table[key]

   -- when there's something in the table
   -- deal with it
   if answer then
      -- we got an answer
      if answer.type == "answer" then
         -- remove the item from the pool
         funcatron.response_table[key] = nil
         -- return the data
         return answer.data, nil
      end

      -- we've got a zero timeout, so don't wait,
      -- just punt
      if timeout == 0 then
         -- remove the item from the pool
         funcatron.response_table[key] = nil
         return nil, ("Key " .. key .. " has no answer and timeout 0")
      end

      -- there's already a wait block.. so wait on it
      if answer.type == "wait" then
         local ok, err = answer.sema:wait(timeout)

         if err then
            -- remove the item from the pool
            funcatron.response_table[key] = nil
            return nil, ("Failed wait " .. err)
         end
         -- recursively check for the answer
         return funcatron.get_response(key, 0)
      end
   else -- nothing in the table

      -- we're not waiting, so just return
      if timeout == 0 then
         return nil, ("No message with key " .. key)
      end

      local start_time = os.time()

      -- create a wait block
      local wait_block = {}
      wait_block.type = "wait"
      wait_block.sema = semaphore.new()
      wait_block.when = os.time()

      -- put it in the table
      funcatron.response_table[key] = wait_block

      -- and wait until we get an answer
      local ok, err = wait_block.sema:wait(timeout)
      if err then
         funcatron.response_table[key] = nil
         return nil, ("Failed wait " .. err .. " duration " ..
                         (os.time() - start_time))
      end
      -- recursively call
      return funcatron.get_response(key, 0)
   end
end

local function heartbeat()
   if not keep_running then
      return
   end

   local rabbit, err = funcatron.rabbit_connection()

   if dev_mode then
      ngx.log(ngx.ALERT, "In heartbeat... and dev mode")
   end

   -- {:action "heartbeat"
   --     :msg-id UUID-string
   --     :from UUID-String
   --     :at currentTimeMillis
   -- }

   if rabbit then
      local msg_uuid = funcatron.uuid()

      local msg = cjson.encode({action="heartbeat",
                                from=funcatron.instance_uuid,
                                at=(os.time() * 1000),
                                host_info={host=http_host, port=http_port},
                                ["msg-id"]=msg_uuid})

      local headers = {["x-type"] = "heartbeat",
         destination=("/amq/queue/" .. tron_queue),
         persistent="false",
         ["content-type"]="application/json",
         receipt=msg_uuid
      }

      rabbit:send(msg, headers)
      rabbit:close()

   end

   if keep_running then
      ngx.timer.at(10, heartbeat)
   end

end

-- register and start listening right away
ngx.timer.at(0.01, register_and_listen)

-- do a heartbeat every 10 seconds
ngx.timer.at(10, heartbeat)

return funcatron

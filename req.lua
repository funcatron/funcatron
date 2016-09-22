local cjson = require("cjson");

local random = math.random

-- generate a UUID
local function uuid()
    local template ='xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'
    return string.gsub(template, '[xy]', function (c)
        local v = (c == 'x') and random(0, 0xf) or random(8, 0xb)
        return string.format('%x', v)
    end)
end

local rid = ngx.var.request_id

-- we need to seed the random # gen based on current time
-- plus some randomness from the nginx request id
math.randomseed(os.time() * rid:byte(1) * rid:byte(2) * rid:byte(3))

local rabbitmqstomp = require("resty/rabbitmqstomp")

local rabbit, err = rabbitmqstomp:new()

rabbit:set_timeout(10000)

local bunny_host = os.getenv("RABBIT_HOST") or
   os.getenv("FUNC_RABBIT_PORT_61613_TCP_ADDR")

local ok, err = rabbit:connect({host=bunny_host})

if err then
   ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

-- get the body
ngx.req.read_body()

local data = ngx.req.get_body_data()

local msg_uuid = rid -- uuid()

local msg = cjson.encode({headers=ngx.req.get_headers(),
                          method=ngx.req.get_method(),
                          uri_args=ngx.req.get_uri_args(),
                          host=ngx.var.host,
                          content_type=ngx.var.content_type,
                          remote_addr=ngx.var.remote_addr,
                          server_protocol=ngx.var.server_protocol,
                          server_port=ngx.var.server_port,
                          server_addr=ngx.var.server_addr,
                          remote_port=ngx.var.remote_port,
                          request_uri=ngx.var.request_uri,
                          request_id=ngx.var.request_id,
                          args=ngx.var.args,
                          remote_user=ngx.var.remote_user,
                          request=ngx.var.request,
                          http_referer=ngx.var.http_referer,
                          http_user_agent=ngx.var.http_user_agent,
                          scheme=ngx.var.scheme,
                          uri=ngx.var.uri,
                          reply_to = msg_uuid,
                          body=ngx.encode_base64(data)})

local headers = {}
headers["destination"] = "/queue/funcatron"
headers["receipt"] = "msg" .. msg_uuid
headers["app-id"] = "funcatron-resty"
headers["persistent"] = "true"
headers["reply-to"]=msg_uuid
headers["x-host"]=ngx.var.host
headers["x-uri"]=ngx.var.uri
headers["x-scheme"]=ngx.var.scheme
headers["content-type"] = "application/json"

local ok, err = rabbit:send(msg, headers)

if err then
   ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

local ok, err = rabbit:subscribe({destination="/queue/" .. msg_uuid,
                                  persistent="false",
                                  id=msg_uuid})

local data, err = rabbit:receive()

if err then
   ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
else
   local response = cjson.decode(data)
   ngx.status = response.status or 200
   for key,value in pairs(response.headers or {})
   do
      ngx.header[key] = value
   end
   local body=ngx.decode_base64(response.body or "")

   ngx.header["Content-Length"] = body:len()

   ngx.print(body)

   return ngx.exit(response.status)
end

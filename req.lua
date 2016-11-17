local cjson = require("cjson");

local funcatron = require("/data/funcatron")

local rid = ngx.var.request_id


if err then
   ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

-- get the body
ngx.req.read_body()

local data = ngx.req.get_body_data()

local msg_uuid = rid

local msg = cjson.encode({headers=ngx.req.get_headers(),
                          method=ngx.req.get_method(),
                          ["uri-args"]=ngx.req.get_uri_args(),
                          host=ngx.var.host,
                          ["content-type"]=ngx.var.content_type,
                          ["remote-addr"]=ngx.var.remote_addr,
                          ["server-protocol"]=ngx.var.server_protocol,
                          ["server-port"]=ngx.var.server_port,
                          ["server-addr"]=ngx.var.server_addr,
                          ["remote-port"]=ngx.var.remote_port,
                          ["request-uri"]=ngx.var.request_uri,
                          ["request-id"]=ngx.var.request_id,
                          args=ngx.var.args,
                          ["remote-user"]=ngx.var.remote_user,
                          request=ngx.var.request,
                          ["http-referer"]=ngx.var.http_referer,
                          ["http-user-agent"]=ngx.var.http_user_agent,
                          scheme=ngx.var.scheme,
                          uri=ngx.var.uri,
                          ["reply-to"] = msg_uuid,
                          ["body-base64-encoded"]=true,
                          body=ngx.encode_base64(data)})

local headers =
   {["x-method"]=ngx.req.get_method(),
      -- ["x-uri-args"]=ngx.req.get_uri_args(),
      ["x-uri"]=ngx.var.uri,
      ["x-host"]=ngx.var.host,
      ["x-remote-addr"]=ngx.var.remote_addr,
      ["x-server-protocol"]=ngx.var.server_protocol,
      ["x-server-port"]=ngx.var.server_port,
      ["x-server-addr"]=ngx.var.server_addr,
      ["x-request-uri"]=ngx.var.request_uri,
      ["x-scheme"]=ngx.var.scheme,
      ["x-uri-args"]=ngx.var.args,
      ["x-request-id"]=ngx.var.request_id}

headers["destination"] = "/amq/queue/funcatron"
headers["receipt"] = "msg" .. msg_uuid
headers["app-id"] = "funcatron-resty"
headers["persistent"] = "false"
headers["reply-to"]=msg_uuid
headers["content-type"] = "application/json"

local rabbit, err = funcatron.rabbit_connection()

if err then
   ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

local ok, err = rabbit:send(msg, headers)

if err then
   ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

local the_answer = 42
local the_fn = function()
   {
      local ok, err = rabbit:subscribe({destination="/queue/" .. msg_uuid,
                                        persistent="false",
                                        id=msg_uuid})

      local data, err = rabbit:receive()

      rabbit:close()

      corountine.yield(data)

   }

   local co = corountine.create(function() {

   })

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

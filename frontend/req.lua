local cjson = require("cjson");

local funcatron = require("/data/funcatron")

local rid = ngx.var.request_id

if ngx.var.uri == '/' then
   funcatron.record_count(ngx.var.host,
                          ngx.var.uri,
                          ngx.req.get_method(),
                          200,
                          ngx.now() - ngx.req.start_time())
   ngx.status = 200
   ngx.header.content_type = "text/html"
   ngx.say('<!DOCTYPE html>\n<html><head><meta content="text/html" http-equiv="Content-Type"><title>Funcatron</title></head><body><br><br><br><br><br><br><br><table align="center" cellpadding="0" cellspacing="0" border="0" width="100%"><tbody><tr><td align="center"><h1 style="margin:0;padding:0;font-family: Tahoma;"><a href="https://funcatron.org">Funcatron</a></h1></td></tr></tbody></table></body></html>')
   return ngx.exit(200)
end

if ngx.var.uri == '/__routes' then
   funcatron.record_count(ngx.var.host,
                          ngx.var.uri,
                          ngx.req.get_method(),
                          200,
                          ngx.now() - ngx.req.start_time())
   ngx.status = 200
   ngx.header.content_type = "application/json"
   ngx.say(cjson.encode({version=funcatron.version,
   routes=funcatron.routing_table}))
   return ngx.exit(200)
end

local target_queue, err = funcatron.route_for(ngx.var.host,
                                              ngx.var.uri)


if err then
   funcatron.record_count(ngx.var.host,
                          ngx.var.uri,
                          ngx.req.get_method(),
                          ngx.HTTP_NOT_FOUND,
                          ngx.now() - ngx.req.start_time())

   ngx.status = ngx.HTTP_NOT_FOUND
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_NOT_FOUND)
end

ngx.log(ngx.ALERT, "Sending message to queue " .. target_queue .. " request " .. rid)

-- get the body
ngx.req.read_body()

local data = ngx.req.get_body_data()

local msg_uuid = rid

local msg = cjson.encode({headers=ngx.req.get_headers(),
                          action="service",
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
                          ["reply-queue"] = funcatron.instance_uuid,
                          ["body-base64-encoded"]=true,
                          body=ngx.encode_base64(data)})

local headers =
   {["x-method"]=ngx.req.get_method(),
      ["x-uri"]=ngx.var.uri,
      ["x-host"]=ngx.var.host,
      ["x-remote-addr"]=ngx.var.remote_addr,
      ["x-server-protocol"]=ngx.var.server_protocol,
      ["x-server-port"]=ngx.var.server_port,
      ["x-server-addr"]=ngx.var.server_addr,
      ["x-request-uri"]=ngx.var.request_uri,
      ["x-scheme"]=ngx.var.scheme,
      ["x-reply-queue"] = funcatron.instance_uuid,
      ["x-uri-args"]=ngx.var.args,
      ["x-request-id"]=ngx.var.request_id}

headers["destination"] = "/amq/queue/" .. target_queue
headers["receipt"] = "msg" .. msg_uuid
headers["app-id"] = "funcatron-resty"
headers["persistent"] = "false"
headers["reply-to"]=msg_uuid
headers["content-type"] = "application/json"

local rabbit, err = funcatron.rabbit_connection()

if err then
   funcatron.record_count(ngx.var.host,
                          ngx.var.uri,
                          ngx.req.get_method(),
                          ngx.HTTP_INTERNAL_SERVER_ERROR,
                          ngx.now() - ngx.req.start_time())

   ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

ngx.log(ngx.ALERT, "Sending to queue " .. target_queue)

local ok, err = rabbit:send(msg, headers)

if err then
   funcatron.record_count(ngx.var.host,
                          ngx.var.uri,
                          ngx.req.get_method(),
                          ngx.HTTP_INTERNAL_SERVER_ERROR,
                          ngx.now() - ngx.req.start_time())

   ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

-- close the rabbit connection
rabbit:close()

local response, err = funcatron.get_response(msg_uuid, 50)

if err then
   funcatron.record_count(ngx.var.host,
                          ngx.var.uri,
                          ngx.req.get_method(),
                          ngx.HTTP_INTERNAL_SERVER_ERROR,
                          ngx.now() - ngx.req.start_time())

   ngx.status = ngx.HTTP_INTERNAL_SERVER_ERROR
   ngx.header.content_type = "text/plain; charset=utf-8"
   ngx.say(err)
   return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
else
   local the_status = response.status or 200

   ngx.status = the_status
   for key,value in pairs(response.headers or {})
   do
      ngx.header[key] = value
   end
   local body=ngx.decode_base64(response.body or "")

   ngx.header["Content-Length"] = body:len()

   ngx.print(body)

   funcatron.record_count(ngx.var.host,
                          ngx.var.uri,
                          ngx.req.get_method(),
                          the_status,
                          ngx.now() - ngx.req.start_time())

   return ngx.exit(response.status)
end

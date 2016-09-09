local cjson = require("cjson");

local rabbitmqstomp = require("resty/rabbitmqstomp")

local rabbit, err = rabbitmqstomp:new()

rabbit:set_timeout(10000)

local bunny_host = os.getenv("RABBIT_HOST") or os.getenv("FUNC_RABBIT_PORT_61613_TCP_ADDR")

local ok, err = rabbit:connect({host=bunny_host})

if err then
  ngx.header.content_type = "text/plain; charset=utf-8"
  ngx.say(err)
  return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

-- get the body
ngx.req.read_body()

local data = ngx.req.get_body_data()

local msg = cjson.encode({headers=ngx.req.get_headers(), method=ngx.req.get_method(),
 uri_args=ngx.req.get_uri_args(), host=ngx.var.host, request_uri=ngx.var.request_uri,
 scheme=ngx.var.scheme, uri=ngx.var.uri, body=data})
local headers = {}
headers["destination"] = "/queue/test"
headers["receipt"] = "msg#1"
headers["app-id"] = "luaresty"
headers["persistent"] = "true"
headers["content-type"] = "application/json"

local ok, err = rabbit:send(msg, headers)

if err then
  ngx.header.content_type = "text/plain; charset=utf-8"
  ngx.say(err)
  return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
end

local ok, err = rabbit:subscribe({destination="/queue/test",
persistent="true", id="123"})

local data, err = rabbit:receive()

if err then
  ngx.header.content_type = "text/plain; charset=utf-8"
  ngx.say(err)
  return ngx.exit(ngx.HTTP_INTERNAL_SERVER_ERROR)
else
  ngx.status = ngx.HTTP_OK
  ngx.header.content_type = "application/json; charset=utf-8"
-- ngx.header.content_type = "text/html; charset=utf-8"

--[[ ngx.say("<p>Yo, Yo, G!</p>" .. cjson.encode(ngx.req.get_headers()) .. "<hr>" .. cjson.encode(ngx.req.get_method())
  .. " " .. cjson.encode(ngx.req.get_uri_args()) .. " " .. ngx.var.host .. " " .. ngx.var.request_uri .. " " ..
  ngx.var.scheme .. " " .. ngx.var.uri .. "<hr>Rabbit Sez:<br>" .. cjson.encode(err or cjson.decode(data)));
]]--

 ngx.say(data)

 return ngx.exit(ngx.HTTP_OK)
end

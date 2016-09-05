local cjson = require("cjson");

local tcp = ngx.socket.tcp

local sock, err = tcp()


ngx.log(1, "Sock err " .. cjson.encode(err))

local ok, err = sock:connect("func-rabbit", 80)

ngx.log(1, "Connect " .. cjson.encode(ok) .. " err " .. cjson.encode(err))


local rabbitmqstomp = require("resty/rabbitmqstomp")

local rabbit, err = rabbitmqstomp:new(nil )

ngx.log(1,"Rabbit " .. cjson.encode(rabbit) .. " err " .. cjson.encode(err))

-- rabbit:set_timeout(2000)

local ok, err = rabbit:connect("dpp.rocks" , 80)

ngx.log(1, "Connect3 ok " .. cjson.encode(ok) .. " err " .. cjson.encode(err));

ngx.say("<p>Yo, Yo, G!</p>" .. cjson.encode(ngx.req.get_headers()) .. "<hr>" .. cjson.encode(ngx.req.get_method())
  .. " " .. cjson.encode(ngx.req.get_uri_args()) .. " " .. ngx.var.host .. " " .. ngx.var.request_uri .. " " ..
  ngx.var.scheme .. " " .. ngx.var.uri);

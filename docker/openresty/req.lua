local cjson = require("cjson");


ngx.say("<p>Yo, Yo, G!</p>" .. cjson.encode(ngx.req.get_headers()) .. "<hr>" .. cjson.encode(ngx.req.get_method())
  .. " " .. cjson.encode(ngx.req.get_uri_args()) .. " " .. ngx.var.host .. " " .. ngx.var.request_uri .. " " ..
  ngx.var.scheme .. " " .. ngx.var.uri);

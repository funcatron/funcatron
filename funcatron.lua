local funcatron = {}

local random = require "resty.random"

local cjson = require("cjson")

local rabbitmqstomp = require("resty/rabbitmqstomp")


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

local bunny_host = os.getenv("RABBIT_HOST") or
   os.getenv("FUNC_RABBIT_PORT_61613_TCP_ADDR")

local bunny_port = tonumber(os.getenv("RABBIT_PORT") or "61613")

local bunny_user = os.getenv("RABBIT_USER") or "guest"

local bunny_pwd = os.getenv("RABBIT_PWD") or "guest"



function funcatron.rabbit_connection()
   local rabbit, err = rabbitmqstomp:new()

   rabbit:set_timeout(10000)

   local ok, err =
      rabbit:connect({host=bunny_host,
                      port=bunny_port,
                      username=bunny_user,
                      password=bunny_pwd})

   return rabbit, err

end

return funcatron

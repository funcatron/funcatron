local Statsd = {}

Statsd.time  = function (bucket, time) Statsd.register(bucket, time .. "|ms") end
Statsd.count = function (bucket, n)    Statsd.register(bucket, n .. "|c") end
Statsd.incr  = function (bucket)       Statsd.count(bucket, 1) end

Statsd.buffer = {} -- this table will be shared per worker process
                   -- if lua_code_cache is off, it will be cleared every request

Statsd.flush = function(sock, host, port)
   if sock then -- send buffer
      pcall(function()
               local udp = sock()
               udp:setpeername(host, port)
               udp:send(Statsd.buffer)
               udp:close()
            end)
   end
   
   -- empty buffer
   for k in pairs(Statsd.buffer) do Statsd.buffer[k] = nil end
end

Statsd.register = function (bucket, suffix, sample_rate)
   table.insert(Statsd.buffer, bucket .. ":" .. suffix .. "\n")
end

return Statsd 
FROM openresty/openresty:latest-xenial

MAINTAINER David Pollak <feeder.of.the.bears@gmail.com>

RUN \
   cd /tmp && \
   curl -fSL https://github.com/wingify/lua-resty-rabbitmqstomp/archive/v0.1.tar.gz -o resty.tar.gz && \
   tar -xzf resty.tar.gz && \
   cd lua-resty-rabbitmqstomp-0.1 && \
   make install && \
   mkdir /data

ADD req.lua /data/

ADD nginx.conf /usr/local/openresty/nginx/conf/

ENTRYPOINT ["/usr/local/openresty/bin/openresty", "-g", "daemon off;  env FUNC_RABBIT_PORT_61613_TCP_ADDR; env RABBIT_HOST;"]

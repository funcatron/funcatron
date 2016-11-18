FROM openresty/openresty:latest-xenial

MAINTAINER David Pollak <feeder.of.the.bears@gmail.com>

RUN \
   cd /tmp && \
   curl -fSL https://github.com/wingify/lua-resty-rabbitmqstomp/archive/v0.1.tar.gz -o resty.tar.gz && \
   tar -xzf resty.tar.gz && \
   cd lua-resty-rabbitmqstomp-0.1 && \
   make install && \
   mkdir /data

RUN \
  cd /tmp && \
  curl -fSL https://raw.githubusercontent.com/bungle/lua-resty-random/master/lib/resty/random.lua -o random.lua && \
  cp random.lua /usr/local/openresty/lualib

RUN apt-get update && \
    apt-get upgrade -y

RUN apt-get install -y dnsutils

ADD req.lua /data/

ADD nginx.conf /usr/local/openresty/nginx/conf/

ADD start_openresty.sh /usr/local/openresty/bin

EXPOSE 80

ENTRYPOINT ["/bin/bash", "/usr/local/openresty/bin/start_openresty.sh"]

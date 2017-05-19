FROM openresty/openresty:latest-xenial

MAINTAINER David Pollak <funcmaster-d@funcatron.org>

RUN apt-get update && \
    apt-get upgrade -y

RUN apt-get install -y dnsutils

RUN mkdir /data

ADD req.lua /data/

ADD funcatron.lua /data/

ADD nginx.conf /usr/local/openresty/nginx/conf/

ADD start_openresty.sh /usr/local/openresty/bin

ADD rabbitmqstomp.tar.gz /tmp/

ADD random.lua /usr/local/openresty/lualib
ADD statsd.lua /usr/local/openresty/lualib

RUN \
   cd /tmp && \
   cd lua-resty-rabbitmqstomp-0.1 && \
   make install && \
   cd /tmp && \
   rm -rf lua-resty-rabbitmqstomp-0.1

EXPOSE 80

ENTRYPOINT ["/bin/bash", "/usr/local/openresty/bin/start_openresty.sh"]

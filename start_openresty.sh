#!/bin/bash

if [ $1 == "dev" ]; then
    sed s/#DEV#//g /usr/local/openresty/nginx/conf/nginx.conf > /tmp/nginx.conf
    cp /tmp/nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
    echo "Running in dev mode. Point your browser to http://localhost:8780"
fi

# FIXME if in mesos and RABBIT_HOST/RABBIT_PORT not defined, use dig to find/define

/usr/local/openresty/bin/openresty -g "daemon off;  env FUNC_RABBIT_PORT_61613_TCP_ADDR; env RABBIT_HOST; env RABBIT_PORT ; env RABBIT_USER ; env RABBIT_PWD"

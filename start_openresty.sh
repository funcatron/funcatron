#!/bin/bash

if [[ "$1" == "dev" ]]; then
    sed s/#DEV#//g /usr/local/openresty/nginx/conf/nginx.conf > /tmp/nginx.conf
    cp /tmp/nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
    echo "Running in dev mode. Point your browser to http://localhost:8780"
fi



if [ ! -z "$MESOS_TASK_ID" ]; then
    echo "Hostname " $(hostname) >&2

    HOST=""
    PORT=""

    host () {
        HOST=$(dig _woof-woofer1-dogs._tcp.marathon.mesos SRV | \
                      grep "IN A" | grep -v "^\;" | \
                      grep -o "[0-9]*\.[0-9]*\.[[0-9]*\.[0-9]*$" | \
                      sed -n 1p)
    }

    port () {
        PORT=$(dig _woof-woofer1-dogs._tcp.marathon.mesos SRV | \
                      grep "IN SRV" | grep -v "^\;" | \
                      grep -o "[0-9]* *[^ ]*$" | grep -o "^[0-9]*" | \
                      sed -n 1p)
    }



    while [ true ] ; do
        host
        port
        echo "Hello Marathon $HOST $PORT" >&2
        sleep 5
    done
    # FIXME if in mesos and RABBIT_HOST/RABBIT_PORT not defined, use dig to find/define
fi


/usr/local/openresty/bin/openresty -g "daemon off;  env FUNC_RABBIT_PORT_61613_TCP_ADDR; env RABBIT_HOST; env RABBIT_PORT ; env RABBIT_USER ; env RABBIT_PWD"

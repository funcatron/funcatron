#!/bin/bash

export INSTANCEID="R-${RANDOM}-${RANDOM}-${RANDOM}-${RANDOM}-${RANDOM}"

if [[ "$1" == "dev" ]]; then
    sed s/#DEV#//g /usr/local/openresty/nginx/conf/nginx.conf > /tmp/nginx.conf
    cp /tmp/nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
    echo "Running in dev mode. Point your browser to http://localhost:8780"
    export DEV_MODE="TRUE"
fi



if [ ! -z "$MESOS_TASK_ID" ]; then
    echo "It's a Meso substrate, so discover _stomp._rabbit-funcatron._tcp.marathon.mesos" 1>&2

    RHOST=""
    RPORT=""

    host () {
        RHOST=$(dig +short rabbit-funcatron.marathon.mesos  |
                       sed -n 1p)
    }

    port () {
        RPORT=$(dig +short _stomp._rabbit-funcatron._tcp.marathon.mesos SRV | \
                       sed -n 1p | \
                       grep -o '[0-9]* *[^ ]*$' | \
                       grep -o '^[0-9]*')
    }

    while [[ -z "$RPORT" || -z "$RHOST" ]] ; do
        echo "In host/port loop: Host: $RHOST, Port: $RPORT" 1>&2
        sleep 1
        host
        port
    done

    export RABBIT_HOST=$RHOST
    export RABBIT_PORT=$RPORT

    echo "Discovered the port... starting OpenResty" 1>&2
else
    echo "Not in an orchestration env... that we know of..." 1>&2
fi

echo "Starting OpenResty"

/usr/local/openresty/bin/openresty -g "daemon off;"

#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

${DIR}/start_rabbit.sh

docker inspect func-resty > /dev/null 2> /dev/null || \
    docker run -d -p 8680:80 --link func-rabbit --restart always \
    --hostname func-resty --name func-resty funcatron/frontend:latest

#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

${DIR}/start_rabbit.sh

docker run -it --rm -p 8780:80 -v ${DIR}/../docker/openresty/:/data --link func-rabbit --hostname func-dev-resty --name func-dev-resty funcatron/openresty:latest

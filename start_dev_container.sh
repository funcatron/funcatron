#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export INSTANCEID="R-${RANDOM}-${RANDOM}-${RANDOM}-${RANDOM}-${RANDOM}"

docker run -it --rm -p 8780:80 \
       -v ${DIR}:/data --link func-rabbit \
       --hostname func-dev-resty --name func-dev-resty \
       funcatron/frontend:latest dev

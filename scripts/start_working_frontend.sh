#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

${DIR}/start_rabbit.sh

docker inspect working-func-resty > /dev/null 2> /dev/null || \
    docker run --rm --net=host \
           --name working-func-resty \
           funcatron/frontend:working &

#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker pull funcatron/tron:latest

docker inspect func-devshim > /dev/null 2> /dev/null || \
    docker run -ti --rm -p 3000:3000  \
           -p 54657:54657 \
           -e TRON_1=--devmode \
           --name func-devshim funcatron/tron:latest

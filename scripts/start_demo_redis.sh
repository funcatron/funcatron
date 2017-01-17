#!/bin/bash

# Start Redis

# if the db doesn't exist, start it
docker inspect func_redis > /dev/null 2> /dev/null || \
    docker run -d --restart=always --name=func_redis -p 6379:6379 redis:latest

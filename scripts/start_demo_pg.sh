#!/bin/bash

# Start the database

# if the db doesn't exist, start it
docker inspect func_db > /dev/null 2> /dev/null || \
    docker run -d --restart=always --name=func_db -p 5433:5432 postgres:latest

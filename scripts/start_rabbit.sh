#!/bin/sh

# Start the RabbitMQ dev container if it's not already
# running

docker inspect func-rabbit > /dev/null 2> /dev/null || \
    docker run -d -p 61613:61613 -p 5672:5672 -p 15672:15672 --restart always --hostname func-rabbit --name func-rabbit byteflair/rabbitmq-stomp

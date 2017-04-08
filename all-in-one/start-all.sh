#!/bin/bash

echo "Starting RabbitMQ"
# start rabbitmq
docker-entrypoint.sh rabbitmq-server > /tmp/rabbitmq.txt 2>&1 &

sleep 4

echo "Starting Nginx"
export RABBIT_HOST=127.0.0.1

/usr/local/openresty/bin/start_openresty.sh > /tmp/openresty.txt 2>&1 &

echo "Starting Tron"

java -jar /root/tron.jar --rabbit_host 127.0.0.1 --runner > /tmp/runner.txt 2>&1 &

java -jar /root/tron.jar --rabbit_host 127.0.0.1 --tron

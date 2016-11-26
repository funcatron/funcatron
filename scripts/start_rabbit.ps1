
docker inspect func-rabbit > $null 2> $null

if (! $?) {
       echo "Starting RabbitMQ"
       
       docker run -d -p 61613:61613 -p 5672:5672 -p 15672:15672 --restart always --hostname func-rabbit --name func-rabbit byteflair/rabbitmq-stomp
       }

# Messages sent over the Message Bus

The Tron and Runners and other parts of the Funcatron system
operate by sending messages to each other over a message bus.

The message bus may be on RabbitMQ, Kafka, Redis, etc.

## Tron to Runner

### Associate Queue with Func Bundle

### Disassociate Queue with Func Bundle

## Runner to Tron

### Awake

```
{:action "awake"
 :type "runner"
 :msg-id UUID-string
 :from UUID-String
 :at currentTimeMillis
 }
```

### Statistics

### Ack Associate/Disassociate

```
{:action "ack"
 :msg-id UUID-string
 :from UUID-String
 :ack-id UUID-string
 :at currentTimeMillis
 }
```

### Heartbeat

```
{:action "heartbeat"
 :msg-id UUID-string
 :from UUID-String
 :at currentTimeMillis
 }
```

## Frontend to Tron

### Awake

```
{:action "awake"
 :type "frontend"
 :msg-id UUID-string
 :from UUID-String
 :at currentTimeMillis
 }
```


### Heartbeat

```
{:action "heartbeat"
 :msg-id UUID-string
 :from UUID-String
 :at currentTimeMillis
 }
```

### Ack Update Routing

## Tron to Frontend

### Update routing

```
{:action "route"
 :msg-id UUID-string
 :routes [{:host hostname :path path :queue queue-name}]
 :at currentTimeMillis
 }
```

## Frontend to Runner

### Service Request

## Runner to Frontend

### Result of Servicing Request

```
{:action "answer"
 :msg-id UUID-string
 :request-id UUID-string
 :answer {:headers [[String, String]] :status http-status-code :body base64str}
 :at currentTimeMillis
 }
```
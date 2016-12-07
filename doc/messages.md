# Messages sent over the Message Bus

The Tron and Runners and other parts of the Funcatron system
operate by sending messages to each other over a message bus.

The message bus may be on RabbitMQ, Kafka, Redis, etc.

## Tron to Runner

### Associate Queue with Func Bundle


```$clojure
{:action "enable"
  :msg-id UUID-string
  :from UUID-String
  :tron-host (fu/compute-host-and-port opts)
  :at currentTimeMillis
  :props map-of-properties
  :sha func-bundle-sha
}
```

### Send a list of all known bundles to the Runner



### Disassociate Queue with Func Bundle

```$clojure
{:action "disable"
  :msg-id UUID-string
  :from UUID-String
  :tron-host (fu/compute-host-and-port opts)
  :at currentTimeMillis
  :sha func-bundle-sha
}
```

### Host Information

Tell the Runners where to find a Tron

```$clojure
{:action      "tron-info"
 :msg-id      (fu/random-uuid)
 :tron-host   {:host hostname :port port}
 :at          (System/currentTimeMillis)
}

```

### List of all Func bundles

```$clojure
{:action  "all-bundles"
 :tron-host {:host hostname :port port}
 :msg-id  (fu/random-uuid)
 :at      (System/currentTimeMillis)
 :bundles [{:sha k :host host :path basePath}]
 }

```

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



### Died

```
{:action "died"
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
 :instance-id Unique-id
 :from UUID-String
 :at currentTimeMillis
 }
```

### Died

```
{:action "died"
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

Sends the routing table, in the order the routes should be consulted
to the frontend

```
{:action "route"
 :msg-id UUID-string
 :routes [{:host hostname :path path :queue queue-name}]
 :at currentTimeMillis
 }
```

### Die

In dev mode, there may be multiple threads for the same Nginx
instance because in dev mode, modules are all reloaded on each
request.

If the Tron receives multiple "awake" messages, it sends a "die"
message to all Frontend instances from the same `instance-id` that
are the most recent "awake" sender. A frontend instance
that receives "die" should stop contacting the Tron.

```
{:action "die"
 :msg-id UUID-string
 :instance-id instance-id-String
 :at     (System/currentTimeMillis)
 }
```

## Frontend to Runner

### Service Request

```
{:headers http-request-headers
 :action "service"
 :method request_method 
 :uri-args uri-args
 :host ngx.var.host
 :content-type ngx.var.content_type
 :remote-addr ngx.var.remote_addr
 :server-protocol ngx.var.server_protocol
 :server-port ngx.var.server_port
 :server-addr ngx.var.server_addr
 :remote-port ngx.var.remote_port
 :request-uri ngx.var.request_uri
 :request-id ngx.var.request_id
 :args ngx.var.args
 :remote-user ngx.var.remote_user
 :request ngx.var.request
 :http-referer ngx.var.http_referer
 :http-user-agent ngx.var.http_user_agent
 :scheme ngx.var.scheme
 :uri ngx.var.uri
 :reply-to the_target_id_for_answer
 :reply-queue the_queue_to_post_answer_to
 :body-base64-encoded true
 :body request-body}
```

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
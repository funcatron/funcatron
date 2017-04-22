# Different modes from Tron

Initially, I (@dpp) was initially planning to have separate packages
for each Funcatron run-time mode... but that's looking less and less
valuable as it means packaging up a ton of Docker containers, etc.

So, here are the various run modes for Tron:

* "Dev Mode" -- Stand-alone... web server on one side and connection to the "shim" on the other. 
   Used during desktop development.
* "Runner Mode" -- The cluster "Runner" that dequeues requests and passes them to the appropriate Func bundle
* "Manager Mode" -- aka "Tron Mode" Monitors Runners and talks to the substrate to launch more Runners. 
   Tells Runners which
   queues to listen to and which Func bundles are associated with which queues.
   
## Dev Mode

This is the simplest mode. If the `--devmode` flag is passed to the Tron
uberjar on start-up, the Tron instance goes into Dev Mode.


A web server fires up at port 3000 (or `--web_port`) and
waits for HTTP request. Also, listens at port 54657 (or `--shim_port`) for a
connection from the [Dev Shim](https://github.com/funcatron/devshim) which will
send the Swagger file to the Tron instance which defines routes. 

Incoming
HTTP requests will be processed based on the Swagger routing. For requests
that match the routing, a message will be sent to the shim which will
invoke a named class (based on the `operationId` field in the Swagger definition)
and return the value to the Tron instance. The Tron instance will wait for
up to `--dev_request_timeout` seconds (default 60) for an answer,
then the Tron instance will respond to the HTTP request with the result
or a 500.

## Tron Mode

There is one "Tron" instance per cluster. The `--tron` flag puts the Tron
into Tron Mode.

The Tron instance fires up an administrative HTTP instance at port 3000 (or `--web_port`)
and waits for posts at `/api/v1/add_func` to upload new Func bundles. `/api/v1/actions`
for enabling/disabling Func Bundles. And `GET` `/api/v1/stats` to get statistics for
the cluster.

Func Bundles are uploaded to the Tron (there's only one Tron per cluster)
and the Tron looks through the Func Bundle (currently an [UberJar](http://imagej.net/Uber-JAR),
but PEX bundles and JavaScript bundlers in the future) and finds the Swagger file (named
`funcatron.json`, `funcatron.yml`, or `funcatron.yaml`) and uses that file to determine
the routing.
 
Routing is based on Swagger's `host` and `basePath` fields. The `host` and `basePath`
fields **must** be unique for all Func Bundles.

The Tron communicates with other parts of the system via the message queue.
Tron will listen for messages on the `for_tron` queue (modifiable with the `--tron_queue` option).

Each other part of the system notifies the Tron that it's available and heart-beats with
the Tron via the Tron's queue. Each part of the system creates a randomly named queue to
receive messages from the Tron instance.


## Runner Mode

Funcatron on a cluster has different pieces that each scale independently. 
The "Runner" actually runs the code in the Func bundles.

The `--runner` flag puts the Tron instance into Runner mode.

So... how does
the Runner learn of the Func bundles?


# Messages

Here are the messages to/from Tron:




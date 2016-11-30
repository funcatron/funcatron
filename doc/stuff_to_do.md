# Stuff to do/think about

Funcatron is a complex system that needs to be highly available,
rolling upgradeable, and generally "just work". This document contains
some of the stuff to think about.

## Versioning messages

Messages -- how can we version message between Funcatron elements (current elements: Frontend, Tron, Runner)?

## Where to store uploaded files

All Funcatron elements run in ephemeral Docker containers...

Where do we store uploaded Func bundles?

* Every node stores all the known bundles and
  when the Tron fires up, it asks for all the bundles?
* Some shared filesystem?

## What about large HTTP request bodies

Shared filesystem again?

Or HTTP direct connection?

## And large HTTP responses?

Shared filesystem?

HTTP direct connection?

Could something like [Dynamic Routing](https://openresty.org/en/dynamic-routing-based-on-redis.html) work?

And how would that impact using queue depth to choose
how to fire up new Runners?


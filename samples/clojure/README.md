# Clojure Funcatron Example 

This is a simple Clojure project built with [`lein`](http://leiningen.org/)

To build a Funcatron deployable JAR file, type:

```
lein do clean, test, uberjar
```

Those commands tell lein to clean any artifacts, compile, test,
and build an [uberjar](http://asymmetrical-view.com/2010/06/08/building-standalone-jars-wtih-leiningen.html)
of the compiled Clojure code as well as all the dependencies.

The compiled JAR file can be found at
`target/target/clojure_sample-0.1.0-SNAPSHOT-standalone.jar`.

To upload the build to your Funcatron cluster:

```shell
wget -O - --post-file=target/clojure_sample-0.1.0-SNAPSHOT-standalone.jar \
     http://<TRON_HOST>:<TRON_PORT>/api/v1/add_func
```

See [this blog post](https://blog.goodstuff.im/funcatron_mesos_now#upload-and-enable-the-code)
for more information on how to upload and enable Func Bundles.

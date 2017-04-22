# Scala Funcatron Example

This is a simple Scala project built with [`sbt`](http://www.scala-sbt.org/).

To build a Funcatron deployable JAR file, type:

```
sbt clean assembly
```

That command tell sbt to clean any artifacts, compile,
and build an [assembly](https://github.com/sbt/sbt-assembly)
of the compiled Scala code as well as all the dependencies.

The compiled JAR file can be found at
`target/scala-2.11/scala_sample-assembly-1.0.jar`.

To upload the build to your Funcatron cluster:

```shell
wget -O - --post-file=target/scala-2.11/scala_sample-assembly-1.0.jar \
     http://<TRON_HOST>:<TRON_PORT>/api/v1/add_func
```

See [this blog post](https://blog.goodstuff.im/funcatron_mesos_now#upload-and-enable-the-code)
for more information on how to upload and enable Func Bundles.

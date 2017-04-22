# Java Funcatron Example using Maven

This is a simple Java project built with [`Maven`](https://maven.apache.org/)

To build a Funcatron deployable JAR file, type:

```
mvn clean package
```

Those commands tell maven to clean any artifacts, compile,
and build an [assembly](http://maven.apache.org/plugins/maven-assembly-plugin/)
of the compiled Java code as well as all the dependencies.

The compiled JAR file can be found at
`target/java_sample-0.1-SNAPSHOT-jar-with-dependencies.jar`.

To upload the build to your Funcatron cluster:

```shell
wget -O - --post-file=target/java_sample-0.1-SNAPSHOT-jar-with-dependencies.jar \
     http://<TRON_HOST>:<TRON_PORT>/api/v1/add_func
```

See [this blog post](https://blog.goodstuff.im/funcatron_mesos_now#upload-and-enable-the-code)
for more information on how to upload and enable Func Bundles.

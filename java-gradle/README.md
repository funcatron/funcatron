# Java Funcatron Example using Gradle

This is a simple Java project built with [`gradle`](https://gradle.org/)

To build a Funcatron deployable JAR file, type:

```
gradle clean
gradle shadowJar
```

Those commands tell gradle to clean any artifacts, compile,
and build an [ShadowJar](https://github.com/johnrengelman/shadow)
of the compiled Java code as well as all the dependencies.

The compiled JAR file can be found at
`build/libs/java-gradle-all.jar`.

To upload the build to your Funcatron cluster:

```shell
wget -O - --post-file=build/libs/java-gradle-all.jar \
     http://<TRON_HOST>:<TRON_PORT>/api/v1/add_func
```

See [this blog post](https://blog.goodstuff.im/funcatron_mesos_now#upload-and-enable-the-code)
for more information on how to upload and enable Func Bundles.

_NOTE_: If you have already enabled the [java](../java) sample
you will want to disable it before enabling this **java-gradle**
sample as they both use the `/simple/java` URI. For example:

```shell
 curl -H "Content-Type: application/json" -d '{"sha":"JAVA_SHA"}' -X POST http://FUNCATRON_SERVER:FUNCATRON_PORT/api/v1/disable
 curl -H "Content-Type: application/json" -d '{"sha":"JAVA_GRADLE_SHA", "props": {"key": "value"}}' -X POST http://FUNCATRON_SERVER:FUNCATRON_PORT/api/v1/enable

```

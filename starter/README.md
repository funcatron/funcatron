# Starter Maven Archetype for Funcatron

A [Maven](http://maven.apache.org/) [Archetype](http://maven.apache.org/components/archetype/maven-archetype-plugin/)
that makes it super easy for a Java developer to get started with
Funcatron.

To use the archetype:

```
mvn org.apache.maven.plugins:maven-archetype-plugin:2.4:generate -X -B  \
    -DarchetypeGroupId=funcatron \
    -DarchetypeArtifactId=starter \
    -DarchetypeVersion=0.3.0-SNAPSHOT \
    -DgroupId=my.stellar \
    -DartifactId=thang \
    -DarchetypeRepository=https://oss.sonatype.org/content/repositories/snapshots
```

To run the Funcatron dev server:

```
docker run -ti --rm  -e TRON_1=--devmode -p 3001:3001 -p 54657:54657 funcatron/tron:latest
```

To run your project code:

```
mvn compile exec:java
```


Then point your browser to [http://localhost:3001/api/sample](http://localhost:3001/api/sample)


## Contributing

Please see [CONTRIBUTING](https://github.com/funcatron/tron/blob/master/CONTRIBUTING.md) for details on
how to make a contribution.

## Licenses and Support

Funcatron is licensed under an Apache 2 license.

Support is available from the project's founder,
[David Pollak](mailto:funcmaster-d@funcatron.org).

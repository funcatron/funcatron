# Funcatron Services

Code in Funcatron is deployed as a "Func Bundle"... a single
file that contains the entire collection of pieces to run the
services.

For services that run inside the Java Virtual Machine, there may
be special wrappers to get the services to run correctly with
Funcatron.

For example, Clojure services need the `RT.class` loaded to initialize
the Clojure runtime and Clojure dispatches on a package/function
basis rather than a classname basis.

Another example is being able to wrap a Spring Boot application
as a Func Bundle so existing Spring apps can simply be wrapped
and run.

## Contributing

Please see [CONTRIBUTING](https://github.com/funcatron/tron/blob/master/CONTRIBUTING.md) for details on
how to make a contribution.

## Licenses and Support

Funcatron is licensed under an Apache 2 license.

Support is available from the project's founder,
[David Pollak](mailto:feeder.of.the.bears@gmail.com).

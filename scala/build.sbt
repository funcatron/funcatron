name := "scala_sample"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += "Clojars" at "https://clojars.org/repo"

resolvers += "Local Maven" at "file:///Users/dpp/.m2/repository"

libraryDependencies += "funcatron" % "intf" % "0.1.3"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.5"

// https://mvnrepository.com/artifact/com.fasterxml.jackson.module/jackson-module-scala_2.11
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4"

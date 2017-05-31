import uk.gov.hmrc.gitstamp.GitStampPlugin._

name := "scala_sample"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += Resolver.mavenLocal

libraryDependencies += "funcatron" % "intf" % "0.3.0-SNAPSHOT"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.5"

// https://mvnrepository.com/artifact/com.fasterxml.jackson.module/jackson-module-scala_2.11
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.4"

Seq( gitStampSettings: _* )

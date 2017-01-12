(defproject funcatron/tron "0.2.5"
  :description "Route request for Funcatron"
  :url "http://funcatron.org"
  :license {:name "Apache 2.0"}

  :dependencies
  [[org.clojure/clojure "1.9.0-alpha14"]
   [cheshire "5.6.3"]
   ;; https://mvnrepository.com/artifact/io.sarnowski/swagger1st
   [org.zalando/swagger1st "0.24.0"]
   ;; [org.unix4j/unix4j-command "0.3"]

   ;; https://mvnrepository.com/artifact/commons-io/commons-io
   [commons-io/commons-io "2.5"]

   ;; https://mvnrepository.com/artifact/net.razorvine/pyrolite
   [net.razorvine/pyrolite "4.13"]

   [http-kit "2.2.0"]

   [ring "1.5.1"]
   [com.fasterxml.jackson.core/jackson-databind "2.8.5"]
   [com.fasterxml.jackson.module/jackson-module-parameter-names "2.8.5"]
   [com.fasterxml.jackson.datatype/jackson-datatype-jdk8 "2.8.5"]
   [com.fasterxml.jackson.datatype/jackson-datatype-jsr310 "2.8.5"]

   ;; https://mvnrepository.com/artifact/com.mesosphere.mesos.rx.java/mesos-rxjava-protobuf-client
   [com.mesosphere.mesos.rx.java/mesos-rxjava-protobuf-client "0.1.0"]
   ;; https://mvnrepository.com/artifact/com.mesosphere.mesos.rx.java/mesos-rxjava-client
   [com.mesosphere.mesos.rx.java/mesos-rxjava-client "0.1.0"]

   [com.spotify/dns "3.1.4"]

   [com.taoensso/timbre "4.7.4"]

   [org.clojure/tools.cli "0.3.5"]
   [overtone/at-at "1.2.0"]
   [zookeeper-clj "0.9.4"]

   [compojure "1.5.1"]
   [ring/ring-json "0.4.0"]

   [instaparse "1.4.5"]

   [com.cognitect/transit-clj "0.8.293"]

   [camel-snake-kebab "0.4.0"]

   [com.novemberain/langohr "3.6.1"]]

  :manifest
  {"GitHeadRev" ~(fn [x] (some-> (clojure.java.shell/sh "git" "rev-parse" "HEAD") :out .trim))}

  :profiles
  {:dev     {:dependencies [[ring/ring-devel "1.5.0"]
                            [javax.servlet/servlet-api "2.5"]]}
   :uberjar {:aot :all}
   }


  :min-lein-version "2.7.1"

  :repositories {"Maven Central" "https://repo1.maven.org/maven2/"}
  :plugins [[lein-virgil "0.1.3"]
            [lein-codox "0.10.2"]]
  :source-paths ["src/clojure"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :main funcatron.tron.core
  :target-path "target/%s"
  )

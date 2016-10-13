(defproject funcatron/tron "0.1.0-SNAPSHOT"
  :description "Route request for Funcatron"
  :url "http://funcatron.org"
  :license {:name "Apache 2.0"}

  :dependencies [
                 [org.clojure/clojure "1.9.0-alpha13"]
                 [cheshire "5.6.3"]
                 ;; https://mvnrepository.com/artifact/io.sarnowski/swagger1st
                 [io.sarnowski/swagger1st "0.21.0"]
                 [org.unix4j/unix4j-command "0.3"]
                 [clj-logging-config/clj-logging-config "1.9.12"]
                 [org.slf4j/slf4j-api "1.7.13"]
                 [org.slf4j/jul-to-slf4j "1.7.13"]
                 [org.slf4j/jcl-over-slf4j "1.7.13"]
                 [org.apache.logging.log4j/log4j-api "2.4.1"]
                 [org.apache.logging.log4j/log4j-core "2.4.1"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.4.1"]
                 ;; https://mvnrepository.com/artifact/commons-io/commons-io
                 [commons-io/commons-io "2.5"]

                 ;; https://mvnrepository.com/artifact/net.razorvine/pyrolite
                 [net.razorvine/pyrolite "4.13"]

                 [ring "1.5.0"]
                 [com.fasterxml.jackson.core/jackson-databind "2.6.3"]
                 [dragonmark/util "0.1.3"]

                 [com.novemberain/langohr "3.6.1"]]

  :profiles {:dev {:dependencies [                          ;;ring/ring-mock "0.3.0"]
                                  ;; [ring/ring-core "1.4.0"]
                                  [ring/ring-devel "1.5.0"]
                                  [javax.servlet/servlet-api "2.5"]


                                  ;; [criterium "0.4.3"]
                                  ]}
             :uberjar {:aot :all}
             }


  :min-lein-version "2.7.1"

  :source-paths      ["src/clojure"]
  :java-source-paths ["src/java"]
  :javac-options     ["-target" "1.8" "-source" "1.8"]
  :main ^:skip-aot funcatron.tron.core
  :target-path "target/%s"
  )

(defproject clojure_sample "0.2.5"
  :description "Clojure Funcatron sample"
  :url "http://funcatron.org"
  :license {:name "Apache 2.0"}
  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [funcatron/intf "0.2.5"]
                 [funcatron/clojure-service "0.2.5"]
                 ]

  :manifest
  {"GitHeadRev" ~(fn [x] (some-> (clojure.java.shell/sh "git" "rev-parse" "HEAD") :out .trim))}

  :plugins [[lein-codox "0.10.2"]]

  :profiles {
             ;; activated automatically during uberjar
             :uberjar {:aot :all}
             :test {:aot :all
                    :dependencies [
                                   [com.fasterxml.jackson.core/jackson-databind "2.8.5" :scope "test"]
                                   ]}
             }

  )

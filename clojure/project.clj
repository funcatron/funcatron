(defproject clojure_sample "0.2.1"
  :description "Clojure Funcatron sample"
  :url "http://funcatron.org"
  :license {:name "Apache 2.0"}
  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [funcatron/intf "0.2.2"]
                 ]

  :manifest
  {"GitHeadRev" ~(fn [x] (some-> (clojure.java.shell/sh "git" "rev-parse" "HEAD") :out .trim))}

  :profiles {
             ;; activated automatically during uberjar
             :uberjar {:aot :all}
             :test {:aot :all
                    :dependencies [
                                   [com.fasterxml.jackson.core/jackson-databind "2.8.5" :scope "test"]
                                   ]}
             }

  )

(defproject tron "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Apache 2.0"}

  :dependencies [
                 [org.clojure/clojure "1.9.0-alpha11"]
                 [cheshire "5.6.3"]
                 [com.novemberain/langohr "3.6.1"]]

  :main ^:skip-aot tron.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

(defproject clojure_sample "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [funcatron/intf "0.2.1"]
                 ]

  :profiles {
             ;; activated automatically during uberjar
             :uberjar {:aot :all}
             :test {:aot :all
                    :dependencies [
                                   [com.fasterxml.jackson.core/jackson-databind "2.8.5" :scope "test"]
                                   ]}
             }

  )

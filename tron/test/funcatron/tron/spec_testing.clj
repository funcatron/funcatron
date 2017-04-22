(ns funcatron.tron.spec-testing
  (:require
    [clojure.test :refer [deftest testing are is run-tests]]
    [clojure.spec.test :as stest]))

(set! *warn-on-reflection* true)

(defn check-ns [ns]
  (-> ns stest/enumerate-namespace stest/check stest/summarize-results))

(defmacro check-nses [nses]
  `(deftest spec-testing
     ~@ (map (fn [ns]
               `(is (let [~'result (check-ns ~ns)]
                      (= (:check-passed ~'result)
                         (:total ~'result)))))
             nses)))

(check-nses ['funcatron.tron.util])

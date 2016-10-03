(ns funcatron.tron.util-test
  (:require
    [clojure.test :refer :all]
    [funcatron.tron.util :refer :all])
  (:import (java.util UUID)
           (java.util.function Function)))


(set! *warn-on-reflection* true)

(deftest ifn-to-func
  (let [uuid (UUID/randomUUID)
        f (fn [_] uuid)]
    (is (= uuid (.apply (to-java-function f) nil)))
    )
  )

(deftest func-to-func
  (let [uuid (UUID/randomUUID)
        f (reify Function
            (apply [this _] uuid)
            )]
    (is (= uuid (.apply (to-java-function f) nil)))
    )
  )

(def data
  [{:foo [{:bar 55} 33 "Hello"] :bar "Dogs"}
   "Hello, World!!"
   (.getBytes "Hello, World!!" "UTF-8")
   ])

(deftest round-trip
  (doseq [d data]
    (let [[t v] (to-byte-array d)
          back (fix-payload t v)]
      (is (= d back))
      )))



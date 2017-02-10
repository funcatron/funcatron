(ns funcatron.sample.clojure.funcs-test
  (:require [clojure.test :refer :all]
            [funcatron.sample.clojure.funcs :refer :all])
  (:import (funcatron.intf.impl ContextImpl)
           (java.util.logging Logger)
           (com.fasterxml.jackson.databind ObjectMapper)
           (funcatron.intf MetaResponse)))


(set! *warn-on-reflection* true)

(def ^ObjectMapper jackson (ObjectMapper.))

(ContextImpl/initContext
  {}
  (-> (fn [])
      .getClass
      .getClassLoader)
  (Logger/getAnonymousLogger))



(deftest simple-test
  (testing "Simple"
    (let [result (simple_get {} (ContextImpl. {} (Logger/getAnonymousLogger)))
          res-json (.writeValueAsString jackson result)]
      (is (instance? String res-json))
      (is (>= (.indexOf ^String res-json "bools") 0))
      )
    ))


(deftest Not_POST_DELETE_on_post_or_delete
  (let [result (post_or_delete nil (ContextImpl. {"parameters"     {"path" {"cnt" 42}}
                                                  "request-method" "get"}
                                                 (Logger/getAnonymousLogger)))]
    (is (instance? MetaResponse result))
    (is (= 400 (.getResponseCode ^MetaResponse result)))
    ))

(deftest DELETE_on_post_or_delete
  (let [result (post_or_delete nil (ContextImpl. {"parameters"     {"path" {"cnt" 45}}
                                                  "request-method" "delete"}
                                                 (Logger/getAnonymousLogger)))
        res-json (.writeValueAsString jackson result)]
    (is (contains? result "name"))
    (is (contains? result "age"))
    (is (= 45 (result "age")))
    (is (string? res-json))
    )
  )

(deftest POST_on_post_or_delete
  (let [result (post_or_delete {"name" "David" "age" 33}
                               (ContextImpl. {"parameters"     {"path" {"cnt" 3}}
                                              "request-method" "post"}
                                             (Logger/getAnonymousLogger)))
        res-json (.writeValueAsString jackson result)]

    (is (sequential? result))
    (is (= 3 (count result)))
    (is (contains? (get result 0) "age"))
    (is (= 34 (get-in result [0 "age"])))
    (is (string? res-json))
    )
  )


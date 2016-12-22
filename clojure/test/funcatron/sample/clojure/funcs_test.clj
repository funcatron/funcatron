(ns funcatron.sample.clojure.funcs-test
  (:require [clojure.test :refer :all]
            )
  (:import (funcatron.intf.impl ContextImpl)
           (java.util.logging Logger)
           (funcatron.sample.clojure SimpleGet PostOrDelete)
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
    (let [s (SimpleGet.)
          result (.apply s {} (ContextImpl. {} (Logger/getAnonymousLogger)))
          res-json (.writeValueAsString jackson result)]
      (is (instance? String res-json))
      (is (>= (.indexOf ^String res-json "bools") 0))
      )
    ))


(deftest Not_POST_DELETE_on_PostOrDelete
  (let [pod (PostOrDelete.)
        result (.apply pod nil (ContextImpl. {"parameters"     {"path" {"cnt" 42}}
                                              "request-method" "get"}
                                             (Logger/getAnonymousLogger)))]
    (is (instance? MetaResponse result))
    (is (= 400 (.getResponseCode ^MetaResponse result)))
    ))

(deftest DELETE_on_PostOrDelete
  (let [pod (PostOrDelete.)
        result (.apply pod nil (ContextImpl. {"parameters"     {"path" {"cnt" 45}}
                                              "request-method" "delete"}
                                             (Logger/getAnonymousLogger)))
        res-json (.writeValueAsString jackson result)]
    (is (contains? result "name"))
    (is (contains? result "age"))
    (is (= 45 (result "age")))
    (is (string? res-json))
    )
  )

(deftest POST_on_PostOrDelete
  (let [pod (PostOrDelete.)
        result (.apply pod {"name" "David" "age" 33}
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


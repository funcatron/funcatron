(ns
  ^{
    :doc    "Simple functions to walk standard Clojure and Java collections and convert keyword keyed Maps
to String keyed maps and vice versa"}
  funcatron.tron.walk
  (:import (java.util List Map Map$Entry)
           (com.rabbitmq.client LongString)))


(defn stringify-keys
  "Recursively transforms all map keys from keywords to strings."
  [m]
  (cond
    (instance? LongString m) (.toString ^LongString m)
    (vector? m) (mapv stringify-keys m)
    (instance? List m) (map stringify-keys m)
    (instance? Map m) (into {} (map (fn [^Map$Entry me]
                                      (let [k (.getKey me)]
                                        [(if (keyword? k) (name k) k) (stringify-keys (.getValue me))]
                                        )) (.entrySet ^Map m)))

    :else m
    )
  )

(defn keywordize-keys
  "Recursively transforms all map keys from keywords to strings."
  [m]
  (cond
    (instance? LongString m) (.toString ^LongString m)
    (vector? m) (mapv stringify-keys m)
    (instance? List m) (map stringify-keys m)
    (instance? Map m) (into {} (map (fn [^Map$Entry me]
                                      (let [k (.getKey me)]
                                        [(if (string? k) (keyword k) k) (keywordize-keys (.getValue me))]
                                        )) (.entrySet ^Map m)))

    :else m
    )
  )

(ns funcatron.tron.store.zookeeper
  (:require [zookeeper :as zk]
            [funcatron.tron.options :as opts]
            [funcatron.tron.store.shared :as shared]
            [clojure.string :as cs]
            [funcatron.tron.util :as fu])
  (:import [funcatron.abstractions StableStore]
           (java.net URLEncoder)))

(defn- build-path
  "Builds a path from the key"
  [^String key]
  (str "/funcatron/" (URLEncoder/encode key "UTF-8"))
  )

(defn build-zk-store
  "Builds a StableStore for ZooKeeper"
  []
  (let [zk (zk/connect (cs/join "," (or
                                      (get-in @opts/command-line-options [:options :zookeeper_host])
                                      ["localhost"])))]
    (zk/create zk "/funcatron" :persistent? true)
    (reify StableStore
      (get [this key]
        (let [{:keys [data]} (zk/data zk (build-path key))]
          (if data (fu/transit-decode data) nil)))
      (put [this key value]
        (let [bytes (fu/transit-encode value)]
          (zk/set-data zk (build-path key) bytes 0)))
      (remove [this key]
        (zk/delete zk (build-path key))))

    ))

(defmethod shared/dispatch-wire-store "zookeeper"
  [opts]
  (build-zk-store ))

(defmethod shared/dispatch-wire-store "zk"
  [opts]
  (build-zk-store))

(defmethod shared/dispatch-wire-store nil
  [opts]
  (build-zk-store))

(defmethod shared/dispatch-wire-store :default
  [opts]
  (build-zk-store))
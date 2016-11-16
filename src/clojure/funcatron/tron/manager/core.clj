(ns funcatron.tron.manager.core
  (:require [zookeeper :as zk]
            [clojure.string :as cs]
            [funcatron.tron.options :as opts]))

(defonce zk-client (atom nil) )

(defn- connect-to-zookeeper
  "Set up the connection to Zookeeper"
  []
  (when-not @zk-client
    (let [zk (zk/connect (cs/join "," (get-in @opts/command-line-options [:options :zookeeper_host])) )]
      (reset! zk-client zk)
      (zk/create @zk-client "/funcatron" :persistent? true)
      (zk/create @zk-client "/funcatron/state" :persistent? true)
      (zk/create @zk-client "/funcatron/time" :persistent? true)
      ))
  )
(ns funcatron.tron.core
  (:require [funcatron.tron.brokers.shared :as shared]
            [funcatron.tron.brokers.rabbitmq :as rabbit]
            [funcatron.tron.routers.jar-router :as jarr])

  (:gen-class)
  (:import [net.razorvine.pyro NameServerProxy])
  )

(set! *warn-on-reflection* true)



(defonce wired (atom nil))

(defn wire-it-up
  "Wire up the RabbitMQ listner and such"
  []
  (when-let [listener @wired]
    (shared/close-all-listeners listener))
  (reset! wired nil)
  (let [listener (rabbit/create-broker)
        handler (jarr/build-router "resources/test.jar" true)
        ]
    (.listenToQueue listener "funcatron" handler)
    (reset! wired listener)
    )

  )






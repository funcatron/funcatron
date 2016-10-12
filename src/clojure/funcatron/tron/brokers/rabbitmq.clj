(ns funcatron.tron.brokers.rabbitmq
  "Create a MessageBroker instance that talks to RabbitMQ"
  (:require [dragonmark.util.props :as d-props]
            [clojure.tools.logging :as log]
            [langohr.core :as lc]
            [langohr.basic :as lb]
            [funcatron.tron.util :as f-util]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons]
            [funcatron.tron.brokers.shared :as shared]
            )
  (:import (funcatron.abstractions MessageBroker)
           (java.util.concurrent ConcurrentHashMap)
           (com.rabbitmq.client Channel)
           (java.util.function Function)
           (funcatron.helpers Tuple2)))


(set! *warn-on-reflection* true)

;; FIXME add more logging


(defn- build-rabbit-handler
  [^Function handler ^MessageBroker broker]
  (fn [^Channel ch metadata payload]
    (let [
          delivery-tag (:delivery-tag metadata)
          message (shared/build-message broker
                                 metadata payload
                                 (fn [] (.basicAck ch delivery-tag false))
                                 (fn [re-queue] (.basicNack ch delivery-tag false re-queue)))
          ]
      (println "Yo!!")
      (future
        (println "In future")
        (try
          (.apply handler message)
          (.basicAck ch delivery-tag false)
          (catch Exception e
            (do
              (.basicNack ch delivery-tag false false)
              (log/error e "Failed to dispatch")
              (throw e)))))

      ))
  )

(defn ^MessageBroker create-broker
  "Create a RabbitMQ MessageBroker instance"
  ([] (create-broker (::rabbit-connection @d-props/info)))
  ([params]
   (let [rabbit-props (or params {})
         listeners (ConcurrentHashMap.)]
     (log/trace "About to open RabbitMQ Connection using " rabbit-props)
     (try
       (let [conn (lc/connect rabbit-props)]
         (log/trace "Openned RabbitMQ Connection")
         (reify MessageBroker
           (isConnected [this] (.isOpen conn))
           (listenToQueue [this queue-name handler]
             (let [ch (lch/open conn)
                   _ (.queueDeclare ch queue-name false false false {})
                   handler (build-rabbit-handler handler this)

                   tag (lcons/subscribe ch queue-name handler {:auto-ack false})
                   kill-func (fn []
                               (.remove listeners tag)
                               (lb/cancel ch tag)
                               (.close ch))]
               (.put listeners tag (Tuple2. queue-name kill-func))
               kill-func))

           (sendMessage [this queue-name metadata message]
             (with-open [ch (lch/open conn)]
               (let [[ct bytes] (f-util/to-byte-array message)
                     metadata (f-util/keywordize-keys metadata)
                     metadata (merge metadata
                                     (when ct {:content-type ct}))
                     ]
                 (lb/publish ch "" queue-name
                             bytes
                             metadata
                             ))))

           (listeners [this]
             (into [] (.values listeners)))

           (close [this] (.close conn))))

       (catch Exception e
         (do
           (log/error e "Failed to open RabbitMQ Connection using " rabbit-props)
           (throw e)))))
    ))




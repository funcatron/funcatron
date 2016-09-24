(ns funcatron.tron.rabbitmq.broker
  "Create a MessageBroker instance that talks to RabbitMQ"
  (:require [dragonmark.util.props :as d-props]
            [clojure.tools.logging :as log]
            [langohr.core :as lc]
            [langohr.basic :as lb]
            [funcatron.tron.util :as f-util]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons]
            [clojure.walk :as walk])
  (:import (funcatron.abstractions MessageBroker MessageBroker$ReceivedMessage)
           (java.util.concurrent ConcurrentHashMap)
           (javafx.util Pair)
           (com.rabbitmq.client Channel)))


(set! *warn-on-reflection* true)

;; FIXME add more logging

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
                   handler (fn [^Channel ch metadata payload]
                             (let [str-metadata (delay (walk/stringify-keys metadata))
                                   content-type (:content-type metadata)
                                   ^long delivery-tag (:delivery-tag metadata)
                                   body (delay (f-util/fix-payload payload))
                                   message (reify MessageBroker$ReceivedMessage
                                             (metadata [this] @str-metadata)
                                             (contentType [this] content-type)
                                             (body [this] @body)
                                             (ackMessage [this] (.basicAck ch delivery-tag false))
                                             (nackMessage [this re-queue] (.basicNack ch delivery-tag false re-queue))
                                             )]
                               (.apply handler message)))

                   tag (lcons/subscribe ch queue-name handler {:auto-ack false})
                   kill-func (fn []
                               (.remove listeners tag)
                               (lb/cancel ch tag)
                               (.close ch))
                   ]
               (.put listeners tag (Pair. queue-name kill-func))
               kill-func))

           (sendMessage [this queue-name metadata message]
             (with-open [ch (lch/open conn)]
               (lb/publish ch "" queue-name
                           (f-util/to-byte-array message)
                           metadata)))

           (listeners [this]
             (into [] (.values listeners)))

           (close [this] (.close conn))))

       (catch Exception e
         (do
           (log/error e "Failed to open RabbitMQ Connection using " rabbit-props)
           (throw e)))))
     ))


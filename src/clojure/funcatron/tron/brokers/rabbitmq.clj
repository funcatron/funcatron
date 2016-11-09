(ns funcatron.tron.brokers.rabbitmq
  "Create a MessageBroker instance that talks to RabbitMQ"
  (:require [dragonmark.util.props :as d-props]
            [clojure.tools.logging :as log]
            [langohr.core :as lc]
            [langohr.basic :as lb]
            [funcatron.tron.util :as f-util]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons]
            [funcatron.tron.options :as opts]
            [funcatron.tron.brokers.shared :as shared]
            )
  (:import (funcatron.abstractions MessageBroker)
           (java.util.concurrent ConcurrentHashMap)
           (com.rabbitmq.client Channel)
           (java.util.function Function)
           (funcatron.helpers Tuple2)))


(set! *warn-on-reflection* true)

;; FIXME add more logging

(defn- handle-rabbit-request
  "A separate function that handles the request"
  [^Function handler ^MessageBroker broker ^Channel ch metadata payload]
  (try
    (let [metadata (merge metadata (:headers metadata))
          delivery-tag (:delivery-tag metadata)
          message (shared/build-message broker
                                        metadata payload
                                        (fn [] (.basicAck ch delivery-tag false))
                                        (fn [re-queue] (.basicNack ch delivery-tag false re-queue)))]
      (future                                               ;; FIXME do our own thread pool
        (try
          (.apply handler message)
          (.basicAck ch delivery-tag false)
          (catch Exception e
            (do
              (.basicNack ch delivery-tag false false)
              (log/error e "Failed to dispatch")
              (throw e)))))
      nil
      )
    (catch Exception e
      (log/error e "Failed to dispatch request " metadata))))

(defn- build-rabbit-handler
  [^Function handler ^MessageBroker broker]
  (fn [^Channel ch metadata payload]
    (handle-rabbit-request handler broker ch metadata payload)
    ))

(defn- fix-props
  "Override :hosts, :port, :password :username based on command line"
  [props]
  (let [opts (:options @opts/command-line-options)]
    {:hosts    (let [z (or (:rabbit_host opts)
                           (:hosts props)
                           "localhost")]
                 (if (string? z) [z] z)
                 )
     :port     (or (:rabbit_port opts)
                   (:port props)
                   5672)

     :username (or (:rabbit_username opts)
                   (:username props)
                   "guest")

     :password (or (:rabbit_password opts)
                   (:password props)
                   "guest")
     }))

(defn ^MessageBroker create-broker
  "Create a RabbitMQ MessageBroker instance"
  ([] (create-broker (::rabbit-connection @d-props/info)))
  ([params]
   (let [rabbit-props (or params {})
         rabbit-props (fix-props rabbit-props)
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



(defmethod shared/dispatch-wire-queue "rabbit"
  [opts]
  (create-broker nil))

(defmethod shared/dispatch-wire-queue nil
  [opts]
  (create-broker nil))

(defmethod shared/dispatch-wire-queue :default
  [opts]
  (create-broker nil))


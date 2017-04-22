(ns funcatron.tron.brokers.rabbitmq
  "Create a MessageBroker instance that talks to RabbitMQ"
  (:require [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [langohr.core :as lc]
            [langohr.basic :as lb]
            [funcatron.tron.util :as f-util]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons]
            [funcatron.tron.options :as opts]
            [funcatron.tron.brokers.shared :as shared]
            [funcatron.tron.util :as fu])
  (:import (funcatron.abstractions MessageBroker)
           (java.util.concurrent ConcurrentHashMap)
           (com.rabbitmq.client Channel)
           (java.util.function Function)
           (funcatron.helpers Tuple2)))


(set! *warn-on-reflection* true)

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
      (fu/run-in-pool
        (fn []
          (try
            (.apply handler message)
            (.basicAck ch delivery-tag false)
            (catch Exception e
              (do
                (.basicNack ch delivery-tag false false)
                (error e "Failed to dispatch")
                (throw e))))))
      nil
      )
    (catch Exception e
      (error e "Failed to dispatch request " metadata))))

(defn- build-rabbit-handler
  [^Function handler ^MessageBroker broker]
  (fn [^Channel ch metadata payload]
    (handle-rabbit-request handler broker ch metadata payload)
    ))

(def ^:private delay-once (delay
                            (Thread/sleep 2000)
                            42))

(defn- compute-mesos-rabbit-props
  "If we're running in Mesos, use DNS to look up the rabbit host and port"
  []
  (if (fu/in-mesos?)
    (do
      (info "Running in Mesos... looking up RabbitMQ DNS info")
      (loop [cnt 0]
        (let [ret
              (->
                (fu/dns-lookup "_rabbit._rabbit-funcatron._tcp.marathon.mesos")
                first
                )]
          ;; try 8 times to find the RabbitMQ server with some backoff
          (if ret
            {:hosts [(:host ret)] :port (:port ret)}
            (if (< cnt 8)
              (do (Thread/sleep (* 1000 (inc cnt)))
                  (recur (inc cnt)))
              nil))
          )))
    {}))

(defn- fix-props
  "Override :hosts, :port, :password :username based on command line
  or if we're running inside Mesos, Mesos"
  [props]
  (let [opts (:options @opts/command-line-options)]
    (merge
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
       }
      (compute-mesos-rabbit-props)
      )))

(defn ^MessageBroker create-broker
  "Create a RabbitMQ MessageBroker instance"
  ([] (create-broker {}))
  ([params]
   (let [rabbit-props (or params {})
         rabbit-props (fix-props rabbit-props)
         listeners (ConcurrentHashMap.)]
     (info "About to open RabbitMQ Connection using " rabbit-props)
     (try
       (let [conn (lc/connect rabbit-props)]
         (info "Openned RabbitMQ Connection")
         (reify MessageBroker
           (queueDepth [this queue-name]
             (with-open [ch (lch/open conn)]
               (let [answer (.basicGet ch queue-name false)]
                 (if answer
                   (try
                     (-> answer .getMessageCount)
                     (finally (.basicNack ch (-> answer .getEnvelope .getDeliveryTag) false true)))
                   0
                   )
                 )
               ))
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
           (error e "Failed to open RabbitMQ Connection using " rabbit-props)
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

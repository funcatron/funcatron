(ns funcatron.tron.rabbitmq.broker
  "Create a MessageBroker instance that talks to RabbitMQ"
  (:require [dragonmark.util.props :as d-props]
            [clojure.tools.logging :as log]
            [langohr.core :as lc]
            [langohr.basic :as lb]
            [funcatron.tron.util :as f-util]
            [langohr.channel :as lch]
            [langohr.consumers :as lcons]
            [funcatron.tron.walk :as walk])
  (:import (funcatron.abstractions MessageBroker MessageBroker$ReceivedMessage)
           (java.util.concurrent ConcurrentHashMap)
           (com.rabbitmq.client Channel)
           (java.util.function Function)
           (funcatron.helpers Tuple2)
           (clojure.lang IAtom)
           (java.util UUID)))


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
                                   body (delay (f-util/fix-payload content-type payload))
                                   message (reify MessageBroker$ReceivedMessage
                                             (metadata [this] @str-metadata)
                                             (contentType [this] content-type)
                                             (body [this] @body)
                                             (ackMessage [this] (.basicAck ch delivery-tag false))
                                             (nackMessage [this re-queue] (.basicNack ch delivery-tag false re-queue))
                                             )]
                               (try
                                 (.apply handler message)
                                 (.basicAck ch delivery-tag false)
                                 (catch Exception e
                                   (do
                                     (.basicNack ch delivery-tag false true)
                                     (throw e))))
                               ))

                   tag (lcons/subscribe ch queue-name handler {:auto-ack false})
                   kill-func (fn []
                               (.remove listeners tag)
                               (lb/cancel ch tag)
                               (.close ch))
                   ]
               (.put listeners tag (Tuple2. queue-name kill-func))
               kill-func))

           (sendMessage [this queue-name metadata message]
             (with-open [ch (lch/open conn)]
               (let [[ct bytes] (f-util/to-byte-array message)
                     metadata (walk/keywordize-keys metadata)
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

(defn ^MessageBroker create-local-broker
  "Create an in-memory MessageBroker instance. Pass in an atom that contains a Map of queue names."

  ([queue-atom]
   (when (not (map? @queue-atom)) (reset! queue-atom {}))
   (let [listeners (ConcurrentHashMap.)]
     (reify MessageBroker
       (isConnected [this] true)
       (listenToQueue [this queue-name handler]
         (let [atom-holder (atom (atom []))]
           (swap! queue-atom (fn [m]
                               (let [maybe-atom (get m queue-name)]
                                 (if (and (not (nil? maybe-atom))
                                          (instance? IAtom maybe-atom))
                                   (do
                                     (reset! atom-holder maybe-atom)
                                     m
                                     )

                                   (assoc m queue-name @atom-holder)))))
           (let [the-atom @atom-holder
                 uuid (UUID/randomUUID)
                 kill-func (fn [] (remove-watch the-atom uuid))]
             (add-watch the-atom uuid
                        (fn [k r os ns]
                          (future
                            (let [cur @the-atom]
                              (when (not (empty? cur))
                                (let [{:keys [bytes metadata] :as msg} (first cur)]
                                  (reset! the-atom (pop cur))
                                  (let [content-type (or
                                                       (get metadata "content-type")
                                                       (get metadata "Content-Type")
                                                       (get metadata :content-type)
                                                       )
                                        body (delay (f-util/fix-payload content-type bytes))
                                        message (reify MessageBroker$ReceivedMessage
                                                  (metadata [this] metadata)
                                                  (contentType [this] content-type)
                                                  (body [this] @body)
                                                  (ackMessage [this] )
                                                  (nackMessage [this re-queue] (when re-queue
                                                                                 (swap! the-atom conj msg)
                                                                                 ))
                                                  )
                                        ]
                                    (try
                                      (.apply handler message)
                                      (.ackMessage message)
                                      (catch Exception e
                                        (do
                                          (.nackMessage message false)
                                          (throw e)))))))))))

             (.put listeners uuid (Tuple2. queue-name kill-func))
             kill-func
             )))

       (sendMessage [this queue-name metadata message]
         (when-let [the-atom (get @queue-atom queue-name)]
           (let [[ct bytes] (f-util/to-byte-array message)
                 metadata (walk/stringify-keys metadata)
                 metadata (merge metadata
                                 (when ct {"content-type" ct}))
                 ]
             (swap! the-atom conj {:metadata metadata
                                   :bytes    bytes}))))

       (listeners [this]
         (into [] (.values listeners)))

       (close [this] (doseq [^Tuple2 t (.values listeners)]
                       ((._2 t))))))
    ))

(defn listen-to-queue
  "A helper function that wraps a Clojure function in a Function wrapper"
  [^MessageBroker broker ^String queue func]
  (.listenToQueue broker queue (f-util/to-java-function func)))
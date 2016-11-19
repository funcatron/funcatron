(ns funcatron.tron.brokers.shared
  (:require [funcatron.tron.util :as fu]
            [funcatron.tron.options :as the-opts])
  (:import (com.rabbitmq.client LongString)
           (funcatron.abstractions MessageBroker$ReceivedMessage MessageBroker)
           (java.util Base64)
           (funcatron.helpers Tuple2)))

(set! *warn-on-reflection* true)

(defn- fix-rabbit-long-string
  "COnverts RabbitMQ's long string into a String"
  [x]
  (cond
    (instance? LongString x) (.toString ^LongString x)
    :else x))

(defn- delay-metadata
  "Delays the conversion of the metadata"
  [metadata]
  (delay (fu/stringify-keys fix-rabbit-long-string metadata))
  )

(defn- delay-fix-body
  "Converts a byte array of body into the a correct data structure"
  [metadata ^"[B" payload]
  (delay
    (let [basic (fu/fix-payload (:content-type metadata) payload)
          fixed (if (:body basic)
                  (let [body-bytes (if (:body-base64-encoded basic)
                                     (.decode (Base64/getDecoder) ^String (:body basic))
                                     (:body basic))
                        body-bytes (if (or (nil? body-bytes) (= 0 (count body-bytes))) nil body-bytes)
                        ]
                    (assoc basic :body body-bytes)
                    )
                  basic
                  )
          ]
      (fu/stringify-keys fix-rabbit-long-string fixed))))

(defn ^MessageBroker$ReceivedMessage build-message
  "Builds a message from the metadata and payload"
  [^MessageBroker broker metadata ^"[B" payload ack-fn nack-fn]
  (let [str-metadata (delay-metadata metadata)
        content-type (:content-type metadata)
        body (delay-fix-body metadata payload)
        message (reify MessageBroker$ReceivedMessage
                  (metadata [this] @str-metadata)
                  (contentType [this] content-type)
                  (body [this] @body)
                  (rawBody [this] payload)
                  (ackMessage [this] (ack-fn))
                  (nackMessage [this re-queue] (nack-fn re-queue))
                  (messageBroker [this] broker)
                  )]
    message)
  )

(defn close-all-listeners
  "Closes all the listeners for the MessageBroker"
  [^MessageBroker mb]
  (dorun (map (fn [^Tuple2 e] ((._2 e))) (.listeners mb)))
  )

(defn listen-to-queue
  "A helper function that wraps a Clojure function in a Function wrapper"
  [^MessageBroker broker ^String queue func]
  (.listenToQueue broker queue (fu/to-java-function func)))

(defmulti ^MessageBroker dispatch-wire-queue "A multi-method that dispatches to the right queue provider/creator"
          (fn [opts] (-> opts :options :queue_type))
          )

(defn ^MessageBroker wire-up-queue
  "Based on the run-time options, wire up an appropriate message queue."
  []
  (dispatch-wire-queue @the-opts/command-line-options)
  )
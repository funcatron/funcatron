(ns funcatron.tron.rabbit
  (:require [langohr.basic :as lb]
            [dragonmark.util.props :as d-props]
            [cheshire.parse :as json-parse]
            [langohr.consumers :as lcons]
            [clojure.spec :as s]
            [langohr.channel :as lch]
            [clojure.tools.logging :as log]
            [langohr.core :as lc]
            [cheshire.core :as json])
  (:import (com.rabbitmq.client Connection Channel)
           (com.fasterxml.jackson.core JsonFactory)
           (java.util Base64 Map)
           (java.io InputStream)
           (org.apache.commons.io IOUtils)
           ))

(set! *warn-on-reflection* true)


(defonce ^:private rb-conn (atom nil))

(defn- ^Connection ensure-open-connection
  "Makes sure the connection is open"
  []
  (if (not (and @rb-conn (.isOpen ^Connection @rb-conn)))

    (let [rabbit-props (or
                         (::rabbit-connection @d-props/info)
                         {})]
      (log/trace "About to open RabbitMQ Connection using " rabbit-props)
      (try
        (let [conn (lc/connect rabbit-props)]
          (reset! rb-conn conn)
          (log/trace "Openned RabbitMQ Connection")
          conn)
        (catch Exception e
          (do
            (reset! rb-conn nil)
            (log/error e "Failed to open RabbitMQ Connection using " rabbit-props)
            (throw e)))))
    @rb-conn
    ))

(defprotocol ToByteArray
  "Convert the incoming value to a byte array"
  (^"[B" to-byte-array [v] "Convert v to a byte array"))

(extend-type String
  ToByteArray
  (to-byte-array [^String v] (.getBytes v "UTF-8")))

(extend-type (Class/forName "[B")
  ToByteArray
  (to-byte-array [^"[B" v] v))

(extend-type InputStream
  ToByteArray
  (to-byte-array [^InputStream v] (IOUtils/toByteArray v)))

(extend-type Map
  ToByteArray
  (to-byte-array [^Map v] (to-byte-array (json/generate-string v))))

(defn encode-body
  "Base64 Encode the body's bytes. Pass data to `to-byte-array` and Base64 encode the result "
  [data]
  (.encodeToString (Base64/getEncoder) (to-byte-array data))
  )

(defn post-to-queue
  "Post a message to a RabbitMQ queue. `data` will be passed to `to-byte-array` to turn it into bytes."
  [queue-name data meta-data]
  (let [conn (ensure-open-connection)]
    (with-open [ch (lch/open conn)]
      (lb/publish ch "" queue-name
                  (to-byte-array data)
                  meta-data)
      )))

(s/fdef listen-to-queue
        :args (s/cat :queue-name string? :function fn?)
        :ret fn?)


(defn listen-to-queue
  "Listen to a named queue. Each message that comes from the queue is sent to the function.
  returns a function that stops listenings to the queue"
  [queue-name function]

  (let [conn (ensure-open-connection)
        ch (lch/open conn)
        handler (fn [^Channel ch metadata payload]
                  (let [delivery-tag (:delivery-tag metadata)]
                    (try
                      (log/trace "About to process " delivery-tag " for queue " queue-name " metadata " metadata)
                      (function ch metadata payload)
                      (.basicAck ch (:delivery-tag metadata) false)
                      (log/trace "Success/ACK " delivery-tag " for queue " queue-name)

                      ;; ack
                      (catch Exception e
                        (do
                          (log/error e "Failed to process " delivery-tag " NACKing with re-queue")
                          (.basicNack ch (:delivery-tag metadata) false true)
                          ))))
                  )
        _ (.queueDeclarePassive ch queue-name)
        tag (lcons/subscribe ch queue-name handler {:auto-ack false})]
    (fn []
      (lb/cancel ch tag)
      (.close ch))))
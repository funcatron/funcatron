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


(defn post-to-queue
  "Post a message to a RabbitMQ queue. `data` will be passed to `to-byte-array` to turn it into bytes."
  [queue-name data meta-data]
  (let [conn (ensure-open-connection)]
    ))

(s/fdef listen-to-queue
        :args (s/cat :queue-name string? :function fn?)
        :ret fn?)


(defn listen-to-queue
  "Listen to a named queue. Each message that comes from the queue is sent to the function.
  returns a function that stops listenings to the queue"
  [queue-name function]

  )
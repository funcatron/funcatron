(ns funcatron.tron.rabbit
  (:require [langohr.basic :as lb]
            [dragonmark.util.props :as d-props]
            [cheshire.parse :as json-parse]
            [langohr.consumers :as lcons]
            [clojure.spec :as s]
            [langohr.channel :as lch]
            [langohr.core :as lc])
  (:import (com.rabbitmq.client Connection)
           (com.fasterxml.jackson.core JsonFactory)
           (java.util Base64)))

(set! *warn-on-reflection* true)


(defonce ^:private rb-conn (atom nil))



(defn- ensure-open-connection
  "Makes sure the connection is open"
  []
  (if (not (and @rb-conn (.isOpen ^Connection @rb-conn)))
    (let [conn (lc/connect (::rabbit-connection @d-props/info))]
      (reset! rb-conn conn)
      conn)
    @rb-conn
    ))

(defn- json-parse-byte-array
  "JSON Parses a byte array"
  [^"[B" ba]
  (let [parser (.createParser (JsonFactory.) ba)]
    (json-parse/parse parser keyword nil nil)
    )
  )

(defn- parse-and-fix-payload
  "Parses the payload as JSON and then fixes the fields based on content type"
  [^"[B" ba]
  (let [parsed (json-parse-byte-array ba)]
    (if (:body parsed)
      (let [body-bytes (.decode (Base64/getDecoder) ^String (:body parsed))]
        (cond
          (and false (= "application/json" (-> parsed :headers :content-type)))
          (assoc parsed :body (json-parse-byte-array body-bytes))

          :else (assoc parsed :body body-bytes))
        )
      parsed
      )
    )
  )

(s/fdef list-to-queue
        :args (s/cat :queue-name string? :function fn?)
        :ret fn?)

(defn listen-to-queue
  "Listen to a named queue. Each message that comes from the queue is sent to the function.
  returns a function that stops listenings to the queue"
  [queue-name function]

  (let [conn (ensure-open-connection)
        ch (lch/open conn)
        handler (fn [ch metadata payload]
                  (try
                    (function ch metadata payload)
                    ;; ack
                    (catch Exception e
                      (do
                        ;; log
                        ;; nak
                        )))
                  )
        tag (lcons/subscribe ch handler {:auto-ack false})]
    (fn [] (lb/cancel ch tag))))
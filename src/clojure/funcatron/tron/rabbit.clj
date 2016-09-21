(ns funcatron.tron.rabbit
  (:require [langohr.basic :as lb]
            [cheshire.parse :as json-parse])
  (:import (com.rabbitmq.client Connection)
           (com.fasterxml.jackson.core JsonFactory)
           (java.util Base64)))

(set! *warn-on-reflection* true)


(defonce rb-conn (atom nil))



(defn- ensure-open-connection
  "Close any subscription we have"
  []
  (if (and @rb-conn (.isOpen ^Connection @rb-conn))
    @rb-conn

    (let [{:keys [^Connection conn ch tag]} @rb-conn]
      (reset! rb-conn nil)
      (lb/cancel ch tag)
      (.close conn)
      )))

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

(ns funcatron.tron.request-handler
  "Handle requests from the Funcatron (Nginx -> Tron) queue"
  (:require [clojure.spec :as s]
            [cheshire.parse :as json-parse]
            [funcatron.tron.rabbit :as rabbit])
  (:import (java.util Base64)
           (com.fasterxml.jackson.core JsonFactory)))

(s/def ::queue-name string?)

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

(defn message-handler
  [ch meta payload])

(defn register-rabbit-handler
  "Register to handle requests to the incoming Tron queue"
  [the-func]
  (rabbit/listen-to-queue
    (or (::queue-name @info)
        "funcatron")
    the-func)
  )

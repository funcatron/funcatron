(ns tron.core
  (:require [langohr.core :as lc]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.consumers :as lcons]
            [cheshire.core :as json]
            [cheshire.parse :as json-parse]
            [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [io.sarnowski.swagger1st.context :as s1ctx]

            [langohr.basic :as lb])
  (:gen-class)
  (:import (com.fasterxml.jackson.core JsonFactory)
           (com.fasterxml.jackson.databind ObjectMapper)
           (com.rabbitmq.client Connection)
           (java.util Base64 Date)
           (java.net URL URLClassLoader)
           (java.io File ByteArrayOutputStream EOFException)))

(set! *warn-on-reflection* true)

(defonce rb-conn (atom nil))

(def pretty-printer
  (json/create-pretty-printer json/default-pretty-print-options))

(defn close-it
  "Close any subscription we have"
  []
  (if @rb-conn
    (let [{:keys [^Connection conn ch tag]} @rb-conn]
      (reset! rb-conn nil)
      (lb/cancel ch tag)
      (.close conn)
      )))

(defn json-parse-byte-array
  "JSON Parses a byte array"
  [^"[B" ba]
  (let [parser (.createParser (JsonFactory.) ba)]
    (json-parse/parse parser keyword nil nil)
    )
  )

(defn parse-and-fix-payload
  "Parses the payload as JSON and then fixes the fields based on content type"
  [^"[B" ba]
  (let [parsed (json-parse-byte-array ba)]
    (if (:body parsed)
      (let [body-bytes (.decode (Base64/getDecoder) ^String (:body parsed))]
        (cond
          (= "application/json" (-> parsed :headers :content-type))
          (assoc parsed :body (json-parse-byte-array body-bytes))
          :else (assoc parsed :body body-bytes))
        )
      parsed
      )
    )
  )

(defn handle-delivery
  [ch metadata payload]

  (let [payload (parse-and-fix-payload payload)
        payload (assoc payload :slurm (Date.))]


    (let [body (.encodeToString (Base64/getEncoder) (.getBytes (str (json/generate-string payload {:pretty pretty-printer}) "\n\n") "UTF-8"))]
      (lb/publish ch "" (:reply-to metadata) (.getBytes (json/generate-string {:body body
                                                                               :status 200
                                                                               :headers {:content-type "text/plain" :x-snarf "woof"}
                                                                               }) "UTF-8"))))
  )

(defn listen-to-funcatron
  "Listen to Funcatron queue"
  []
  (close-it)
  (let [conn (lc/connect)
        ch (lch/open conn)
        tag (lcons/subscribe ch "funcatron" handle-delivery {:auto-ack true})]
    (reset! rb-conn {:conn conn :ch ch :tag tag})))


(def sample-swagger
  "swagger: '2.0'\n\ninfo:\n  title: Example API\n  version: '0.1'\n\npaths:\n  /helloworld:\n    get:\n      summary: Returns a greeting.\n      operationId: example.api/generate-greeting\n      parameters:\n        - name: firstname\n          in: query\n          type: string\n          pattern: \"^[A-Z][a-z]+\"\n      responses:\n          200:\n              description: say hello")




(comment (defn app
           []
           (-> (s1st/context :yaml-cp "my-swagger-api.yaml")
               (s1st/discoverer)
               (s1st/mapper)
               (s1st/parser)
               (s1st/protector {"oauth2" (s1stsec/allow-all)})
               (s1st/executor))))
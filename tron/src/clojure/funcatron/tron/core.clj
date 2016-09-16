(ns funcatron.tron.core
  (:require [langohr.core :as lc]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.consumers :as lcons]
            [cheshire.core :as json]
            [cheshire.parse :as json-parse]
            [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [funcatron.tron.jars :as tj]
            [clojure.walk :as walk]
            [langohr.basic :as lb])
  (:gen-class)
  (:import (com.fasterxml.jackson.core JsonFactory)
           (com.rabbitmq.client Connection)
           (java.util Base64)
           (funcatron.helpers SingleClassloader)
           (java.io ByteArrayInputStream Reader InputStream)
           (org.apache.commons.io IOUtils)
           (com.fasterxml.jackson.databind ObjectMapper)
           (java.lang.reflect Method Constructor)
           (org.slf4j LoggerFactory)))

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
          (and false (= "application/json" (-> parsed :headers :content-type)))
          (assoc parsed :body (json-parse-byte-array body-bytes))

          :else (assoc parsed :body body-bytes))
        )
      parsed
      )
    )
  )

(def sample-swagger
  "swagger: '2.0'

info:
   title: Example API
   version: '0.1'

basePath: /foo

host: localfoo

paths:
   /helloworld:
     get:
       summary: Returns a greeting.
       operationId: tron.core/generate-greeting
       parameters:
         - name: firstname
           in: query
           required: true
           type: string
           pattern: \"^[A-Z][a-z]+\"
       responses:
           200:
               description: say hello
")

(defn generate-greeting
  [req]

  ;; (clojure.pprint/pprint req)
  {:foo "bar" "name" (-> req :query-params (get "firstname"))}
  )

(defn wrap-response
  "A ring handler that wraps the response with appropriate stuff"
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (cond
        :else {:status 200

               :headers {"Content-Type" "application/json"}
               :body resp})
      )
    )
  )

(defn my-func
      [handler]
      (fn [req]
        (let [resp (handler req)]
          (println "Response:\n" resp)
          resp
          )

        )
      )

(defn resolve-stuff
  [{:keys [classloader]} req]

  (let [^String op-id (get req "operationId")
        clz (.loadClass ^ClassLoader classloader op-id)
        ^Method apply-method (->> (.getMethods clz)
                         (filter #(= "apply" (.getName ^Method %)))
                          first)
        lf-clz (.loadClass ^ClassLoader classloader "org.slf4j.LoggerFactory")
        new-logger-meth (.getMethod lf-clz "getLogger" (into-array Class [String]))

        logger (.invoke new-logger-meth nil (into-array Object [op-id]))
        ctx-clz (.loadClass ^ClassLoader classloader "funcatron.intf.impl.ContextImpl")
        ^Constructor constructor (first (.getConstructors ctx-clz))]

    (fn [req]
      (let [req-s (walk/stringify-keys req)
            the-array (let [a ^"[Ljava.lang.Object;" (make-array Object 2)]
                        (aset a 0 req-s)
                        (aset a 1 logger)
                        a
                        )
            context (.newInstance constructor the-array)
            func-obj (.newInstance clz)
            ]

        (.writeValueAsString (ObjectMapper.)
                             (.invoke apply-method func-obj
                                      (into-array Object [req-s context])))
        )
      )
    )

  )

(defn put-def-in-meta
  "Inserts the context definition in the functions metadata"
  [context func & args]
  (let [ret (apply func context args)]
    (with-meta ret {:swagger (:definition context)})
    )
  )

(defn make-app
  [the-jar]
  (-> {:definition     (:swagger the-jar)
       :chain-handlers (list)}
      (s1st/discoverer)
      (s1st/mapper)
      (s1st/parser)
      (s1st/protector {"oauth2" (s1stsec/allow-all)})
      (s1st/ring wrap-response)
      (put-def-in-meta s1st/executor :resolver (partial resolve-stuff the-jar)))
  )

(defn make-ring-request
  "Takes an OpenResty style request and turns it into a Ring style request"
  [req]
  {:server-port    (read-string (or (:server_port req) "80"))
   :server-name    (:host req)
   :remote-addr    (or (-> req :headers (get "x-remote-addr"))
                       (-> req :remote_addr)
                       )
   :uri            (:uri req)
   :open-resty req
   :query-string   (:args req)
   :scheme         (:scheme req)
   :request-method (.toLowerCase ^String (:method req))
   :protocol       (:server_protocol req)
   :headers        (:headers req)
   :body           (when-let [^"[B" body (:body req)]
                     (when (> (count body) 0)
                       (ByteArrayInputStream. body)))
   }
  )

(defn handle-delivery
  [ch metadata payload]

  (let [payload (parse-and-fix-payload payload)
        ring-request (make-ring-request payload)
        app (make-app (-> (tj/qq)))
        resp (app ring-request)
        ]



    (let [body (:body resp)
          body (cond
                 (instance? String body )
                 (.encodeToString (Base64/getEncoder) (.getBytes ^String body "UTF-8"))

                 (instance? InputStream body)
                 (.encodeToString (Base64/getEncoder) (IOUtils/toByteArray ^InputStream body))

                 :else
                 (.encodeToString (Base64/getEncoder) (.getBytes (json/generate-string body) "UTF-8"))
                 )]
      (lb/publish ch "" (:reply-to metadata) (.getBytes (json/generate-string (assoc resp :body body)) "UTF-8"))))
  )

(defn listen-to-funcatron
  "Listen to Funcatron queue"
  []
  (close-it)
  (let [conn (lc/connect)
        ch (lch/open conn)
        tag (lcons/subscribe ch "funcatron" handle-delivery {:auto-ack true})]
    (reset! rb-conn {:conn conn :ch ch :tag tag})))







(comment )
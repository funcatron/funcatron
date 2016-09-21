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
            [clojure.spec :as s]
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

#_(defn my-func
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










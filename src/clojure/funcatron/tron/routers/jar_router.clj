(ns funcatron.tron.routers.jar-router
  (:require [clojure.spec :as s]
            [io.sarnowski.swagger1st.context :as s1ctx]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [io.sarnowski.swagger1st.core :as s1st]
            [funcatron.tron.util :as f-util]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [cheshire.core :as json]
            [funcatron.tron.util :as fu])
  (:import (java.util.jar JarFile JarEntry)
           (java.io File InputStream)
           (java.net URLClassLoader URL)
           (java.lang.reflect Method Constructor)
           (com.fasterxml.jackson.databind ObjectMapper)
           (funcatron.abstractions Router Router$Message)
           (org.apache.commons.io IOUtils)
           (java.util Base64)))


(set! *warn-on-reflection* true)

(s/def ::jar #(instance? JarFile %))

(s/def ::uuid string?)

(s/def ::classloader #(instance? ClassLoader %))

(s/def ::swagger map?)

(s/def ::jar-info (s/keys :req [::jar ::classloader ::uuid]
                          :opt [::swagger]))

(s/fdef jar-info-from-file
        :args (s/or :item string?
                    :item #(instance? File %)
                    )
        :ret ::jar-info)

(defn jar-info-from-file
  "Take a file object and turn it into a combo classloader and JarFile"
  [item]
  (let [file (clojure.java.io/file item)]
    {::jar         (JarFile. file)
     ::classloader (URLClassLoader. (into-array URL [(-> file .toURI .toURL)]) nil nil)
     ::uuid        (fu/random-uuid)
     }
    ))


(s/fdef get-swagger
        :args (s/cat :jar-info ::jar-info)
        :ret ::swagger)

(defn get-swagger
  "Take the output from jar-info-from-file and get the swagger definition"
  [jar-info]
  (let [^JarFile jar (::jar jar-info)]
    (funcatron.tron.util/get-swagger-from-jar jar)))

(s/fdef update-jar-info-with-swagger
        :args (s/cat :jar-info ::jar-info)
        :ret ::jar-info)

(defn update-jar-info-with-swagger
  "Takes the jar-info and adds the parsed swagger information"
  [jar-info]
  (let [swagger (get-swagger jar-info)]
    (assoc jar-info ::swagger swagger)
    ))

(defn- do-wrap-response
  "Split out so we can modify it"
  [handler req]
  (let [resp (handler req)]
    (cond
      (::raw resp)
      (dissoc resp ::raw)

      :else {:status 200

             :headers {"Content-Type" "application/json"}
             :body resp})
    ))

(defn wrap-response
  "A ring handler that wraps the response with appropriate stuff"
  [handler]
  (fn [req]
    (do-wrap-response handler req)
    )
  )

(defn put-def-in-meta
  "Inserts the context definition in the functions metadata"
  [context func & args]
  (let [ret (apply func context args)]
    (with-meta ret {::swagger (:definition context)})
    )
  )

(defn- exception-to-string
  "converts an Exception into a String"
  [^Throwable e]


  (str (.getMessage e) "\n"
       (clojure.string/join "\n" (map (fn [^StackTraceElement ste] (.toString ste)) (.getStackTrace e))))

  )

(defn- handle-actual-request
  ""
  [^Class clz ^Constructor constructor ^Method apply-method logger
   ^Class meta-clz
   req]
  (let [req-s (f-util/stringify-keys req)
        _ (info (str "Req-s " req-s))
        the-array (let [a ^"[Ljava.lang.Object;" (make-array Object 2)]
                    (aset a 0 req-s)
                    (aset a 1 logger)
                    a
                    )
        context (.newInstance constructor the-array)
        func-obj (.newInstance clz)
        res (try
              (.invoke apply-method func-obj
                       (into-array Object [req-s context]))
              (catch Exception e {::exception e})
              )
        ]

    (info "Res is " res)

    (cond
      (and (map? res) (::exception res))
      {:status 500 :body (exception-to-string (::exception res)) ::raw true}

      (and res (.isInstance meta-clz res))
      {:status 500 :body "FIXME -- deal with metaresponse" ::raw true}

      :else
      (try (.writeValueAsString (ObjectMapper.) res)
           (catch Exception e {:status 500 ::raw true :body (exception-to-string e)}))
      )


    ))

(defn- resolve-stuff
  "Given a ::jar-info and a "
    [{:keys [::classloader]} req]

    (let [^String op-id (get req "operationId")
          clz (.loadClass ^ClassLoader classloader op-id)
          ^Method apply-method (->> (.getMethods clz)
                                    (filter #(= "apply" (.getName ^Method %)))
                                    first)
          lf-clz (.loadClass ^ClassLoader classloader "org.slf4j.LoggerFactory")
          new-logger-meth (.getMethod lf-clz "getLogger" (into-array Class [String]))

          logger (.invoke new-logger-meth nil (into-array Object [op-id]))
          ctx-clz (.loadClass ^ClassLoader classloader "funcatron.intf.impl.ContextImpl")
          meta-clz (.loadClass ^ClassLoader classloader "funcatron.intf.MetaResponse")
          ^Constructor constructor (first (.getConstructors ctx-clz))]

      (fn [req] (handle-actual-request clz constructor apply-method logger meta-clz req))))

(defn make-app
    [the-jar]
    (-> {:definition     (::swagger the-jar)
         :chain-handlers (list)}
        (s1st/discoverer)
        (s1st/mapper)
        (s1st/parser)
        (s1st/protector {"oauth2" (s1stsec/allow-all)})
        (s1st/ring wrap-response)
        (put-def-in-meta s1st/executor :resolver (partial resolve-stuff the-jar)))
    )

(defn- route-to-jar
  "Routes the message to the JAR file"
  [^Router$Message message the-app reply?]
  (let [ring-req (f-util/make-ring-request message)
        resp (the-app ring-req)]

    (info "Resp is " resp)

    (when (and reply? (.replyTo message))
      (let [body (:body resp)
            body (cond
                   (instance? String body)
                   (.encodeToString (Base64/getEncoder) (.getBytes ^String body "UTF-8"))

                   (instance? InputStream body)
                   (.encodeToString (Base64/getEncoder) (IOUtils/toByteArray ^InputStream body))

                   :else
                   (.encodeToString (Base64/getEncoder) (.getBytes (json/generate-string body) "UTF-8"))
                   )]
        (.sendMessage (.messageBroker (.underlyingMessage message)) (.replyTo message) {}
                      (.getBytes (json/generate-string (assoc resp :body body)) "UTF-8"))
        ))

    resp
    ))

(defn ^Router build-router
  "Take a JAR file (as a File or String) and create a Router"
  ([file] (build-router file true))
  ([file reply?]
   (let [the-jar (-> file jar-info-from-file update-jar-info-with-swagger)
         the-app (make-app the-jar)]
     (reify Router
       (routeMessage [this message]
         (route-to-jar message the-app reply?))))))



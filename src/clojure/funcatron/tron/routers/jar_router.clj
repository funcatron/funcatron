(ns funcatron.tron.routers.jar-router
  (:require [clojure.spec :as s]
    ;; [io.sarnowski.swagger1st.context :as s1ctx]
            [taoensso.timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [cheshire.core :as json]
            [funcatron.tron.util :as fu])
  (:import (java.util.jar JarFile)
           (java.io InputStream)
           (java.net URLClassLoader URL)
           (java.lang.reflect InvocationTargetException)
           (funcatron.abstractions Router Router$Message)
           (org.apache.commons.io IOUtils)
           (java.util Base64 Map)
           (java.util.logging Logger)
           (java.util.function BiFunction)))


(set! *warn-on-reflection* true)

(s/def ::jar #(instance? JarFile %))

(s/def ::uuid string?)

(s/def ::classloader #(instance? ClassLoader %))

(s/def ::swagger map?)

(s/def ::jar-info (s/keys :req [::jar ::classloader ::uuid]
                          :opt [::swagger]))

#_(s/fdef jar-info-from-file
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

#_(s/fdef get-swagger
          :args (s/cat :jar-info ::jar-info)
          :ret ::swagger)

(defn get-swagger
  "Take the output from jar-info-from-file and get the swagger definition"
  [jar-info]
  (let [^JarFile jar (::jar jar-info)]
    (funcatron.tron.util/get-swagger-from-jar jar)))

#_(s/fdef update-jar-info-with-swagger
          :args (s/cat :jar-info ::jar-info)
          :ret ::jar-info)

(defn update-jar-info-with-swagger
  "Takes the jar-info and adds the parsed swagger information"
  [jar-info]
  (let [swagger (get-swagger jar-info)]
    (assoc jar-info ::swagger swagger)
    ))

(defn- ^String exception-to-string
  "converts an Exception into a String"
  [^Throwable e]

  (if (and
        (instance? InvocationTargetException e)
        (.getCause e)
        )
    (exception-to-string (.getCause e))

    (let [all-frames (.getStackTrace e)
          [yes maybe] (split-with
                        (fn [^StackTraceElement ste] (not= "jar_router.clj" (.getFileName ste)))
                        all-frames)
          to-show (concat yes (take 7 maybe))
          ]
      (str (.toString e) "\n"
           (clojure.string/join "\n" (map
                                       (fn [^StackTraceElement ste] (.toString ste))
                                       to-show
                                       )))))

  )

(defn- do-wrap-response
  "Split out so we can modify it"
  [handler req]

  (let [resp (try
               (handler req)
               (catch Exception e {:status  500
                                   ::raw    true
                                   :body    (exception-to-string e)
                                   :headers {"content-type" "text/plain"}})
               )
        fixed (cond
                (::raw resp)
                (dissoc resp ::raw)

                :else {:status  200

                       :headers {"Content-Type" "application/json"}
                       :body    resp})

        ;; sneak byte array past the output formatters
        fixed (if (instance? (Class/forName "[B") (:body fixed))
                (assoc fixed ::byte-body (:body fixed))
                fixed
                )
        ]
    fixed))

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


(defn- or-empty-map
  [v]
  (or v {}))

(defn- handle-actual-request
  "Handle the request. Split into its own function so we can modify behavior in the REPL"
  [^BiFunction the-func
   req]

  (try
    (let [req
          (-> req
              fu/restore-body
              (update-in [:parameters :query] or-empty-map)
              (update-in [:parameters :body] or-empty-map)
              (update-in [:parameters :path] or-empty-map))

          resp
          (.apply
            the-func
            (:body req)
            (-> req
                (assoc "$logger" (fu/logger-for (or (::log-props req) {})))
                fu/stringify-keys))

          ret
          (-> resp
              fu/keywordize-keys
              (assoc ::raw true))]
      ret)
    (catch Exception e
      (do
        (warn e "Failed to service request")
        {::raw true
         :status 500
         :headers {"content-type" "text/plain"}
         :body (exception-to-string e)}))
    ))

(defn- resolve-stuff
  "Given a ::jar-info and swagger, "
  [{:keys [::classloader]} swagger-info]
  (fu/within-classloader
    classloader
    (fn []
      (let [^String op-id (get swagger-info "operationId")
            clz (.loadClass ^ClassLoader classloader "funcatron.intf.impl.Dispatcher")
            dispatcher ^BiFunction (.newInstance clz)
            apply-bi-func ^BiFunction (.apply dispatcher op-id
                                              (-> swagger-info
                                                  (assoc "$deserializer" fu/jackson-deserializer)
                                                  (assoc "$serializer" fu/jackson-serializer)
                                                  fu/stringify-keys))]

        (fn [req] (fu/within-classloader
                    classloader
                    (fn [] (handle-actual-request
                             apply-bi-func req))))))))

(defn make-app
  [the-jar]
  (-> {:definition     (::swagger the-jar)
       :chain-handlers (list)}
      (s1st/ring fu/preserve-body)
      (s1st/discoverer)
      (s1st/mapper)
      (s1st/parser)
      (s1st/protector {"oauth2" (s1stsec/allow-all)})
      (s1st/ring wrap-response)
      (put-def-in-meta s1st/executor :resolver (partial resolve-stuff the-jar)))
  )

(defn- route-to-jar
  "Routes the message to the JAR file"
  [^Router$Message message the-app log-props reply?]
  (try
    (let [ring-req (fu/make-ring-request message)
          resp (the-app (assoc
                          ring-req
                          ::log-props
                          (assoc log-props :reply-to (.replyTo message))
                          ))]
      (when (and reply? (.replyTo message))
        (let [resp (assoc resp :body (or (::byte-body resp) (:body resp)))
              resp (dissoc resp ::byte-body)
              body (:body resp)
              resp
              (try
                (assoc resp
                  :body (cond
                          (instance? String body)
                          (.encodeToString (Base64/getEncoder) (.getBytes ^String body "UTF-8"))

                          (instance? (Class/forName "[B") body)
                          (String. (.encode (Base64/getEncoder) ^"[B" body) "UTF-8")


                          (instance? InputStream body)
                          (.encodeToString (Base64/getEncoder) (IOUtils/toByteArray ^InputStream body))

                          :else
                          (.encodeToString (Base64/getEncoder) (.getBytes (json/generate-string body) "UTF-8"))
                          ))
                (catch Exception e {:status  500
                                    :headers {"content-type" "text/plain"}
                                    :body    (.encodeToString (Base64/getEncoder)
                                                              (.getBytes
                                                                (exception-to-string e)
                                                                "UTF-8"))})
                )]

          (.sendMessage
            (.messageBroker (.underlyingMessage message))
            (.replyQueue message)
            {:content-type "application/json"}
            {:action     "answer"
             :msg-id     (fu/random-uuid)
             :request-id (.replyTo message)
             :answer     resp
             :at         (System/currentTimeMillis)
             })))

      resp
      )
    (catch Throwable t
      (do
        (error t "Massive Failure")
        (throw t)))
    ))

(defn- initialize-context
  "Okay... given a JAR and a classloader and such, we initialize the ContextImpl object.
  Return the context version and a function that releases the Context's resources"
  [{:keys [::classloader]} properties log-props]
  (let [classloader ^ClassLoader classloader
        context-impl-clz (.loadClass classloader "funcatron.intf.impl.ContextImpl")

        ]
    (fu/within-classloader
      classloader
      (fn []
        (let [version (try
                        (let [meth (.getMethod context-impl-clz "getVersion" (make-array Class 0))]
                          (.invoke meth nil (make-array Object 0))
                          )
                        (catch Exception e (do (error e "Couldn't get version")
                                               "?")))
              end-life-method (.getMethod context-impl-clz "endLife" (make-array Class 0))
              init-method (.getMethod context-impl-clz "initContext" (into-array Class [Map ClassLoader Logger]))]
          (.invoke init-method nil (into-array Object [(fu/camel-stringify-keys (or properties {})) classloader (fu/logger-for log-props)]))
          [version (fn [] (fu/within-classloader classloader (fn [] (.invoke end-life-method nil (make-array Object 0)))))]
          )))))

(defn ^Router build-router
  "Take a JAR file (as a File or String) and create a Router"
  ([file properties] (build-router file properties true))
  ([file properties reply?]
   (let [{:keys [::swagger ::classloader] :as the-jar}
         (-> file jar-info-from-file update-jar-info-with-swagger)

         swagger (fu/keywordize-keys swagger)
         {:keys [host basePath]} swagger

         queue-name (fu/route-to-sha (:host swagger) (:basePath swagger))

         log-props (merge (fu/version-info-from-classloader classloader)
                          {:queue    queue-name,
                           :host     host,
                           :basePath basePath}
                          (fu/some-when (:$log-props properties) map?))

         [_ end-func] (initialize-context the-jar properties log-props)

         the-app (make-app the-jar)]
     (reify Router
       (host [_] host)
       (basePath [_] basePath)
       (swagger [_] (fu/stringify-keys swagger))
       (nameOfListenQueue [_] queue-name)
       (endLife [_] (end-func))
       (routeMessage [_ message]
         (route-to-jar message the-app log-props reply?))))))



(ns funcatron.tron.routers.jar-router
  (:require [clojure.spec :as s]
    ;; [io.sarnowski.swagger1st.context :as s1ctx]
            [taoensso.timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [io.sarnowski.swagger1st.core :as s1st]
            [funcatron.tron.util :as f-util]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [cheshire.core :as json]
            [funcatron.tron.util :as fu])
  (:import (java.util.jar JarFile)
           (java.io InputStream ByteArrayInputStream)
           (java.net URLClassLoader URL)
           (java.lang.reflect Method Constructor InvocationTargetException AnnotatedType ParameterizedType)
           (funcatron.abstractions Router Router$Message)
           (org.apache.commons.io IOUtils)
           (java.util Base64 Map$Entry Map)
           (java.util.logging Logger)
           (org.w3c.dom Node)
           (java.util.function Function)))


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
               (catch Exception e {:status 500
                                   ::raw true
                                   :body (exception-to-string e)
                                   :headers {"content-type" "text/plain"}})
               )
        fixed (cond
                (::raw resp)
                (dissoc resp ::raw)

                :else {:status 200

                       :headers {"Content-Type" "application/json"}
                       :body resp})

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



(defn- coerse-body-to
  "Convert the body to the type"
  [{:keys [body] :as req} ^Class clz inst ^Method get-decoder-method]

  ;; FIXME deal with XML nodes
  (cond
    (nil? body)
    body

    (-> req :parameters :body)
    (let [m (-> req :parameters :body first second fu/stringify-keys)]
      (if-let [^Function decoder (.invoke get-decoder-method inst (make-array Class 0))]
        (.apply decoder m)
        (.convertValue fu/jackson-json m clz)))

    :else
    (let [
          ^InputStream in
          (cond
            (instance? InputStream body)
            body

            (string? body)
            (ByteArrayInputStream. (.getBytes ^String body "UTF-8"))

            (instance? (Class/forName "[B") body)
            (ByteArrayInputStream. ^"[B" body)

            :else nil
            )
          ]
      (if in (.readValue fu/jackson-json in clz) nil)
      ))
  )

(defn- or-empty-map
  [v]
  (or v {}))

(defn- handle-actual-request
  "Handle the request. Split into its own function so we can modify behavior in the REPL"
  [^Class clz
   ^Constructor constructor
   ^Method apply-method
   ^Method get-encoder-method
   ^Method get-decoder-method
   ^Class data-class
   ^Class meta-clz
   req]

  (let [logger (fu/logger-for (or (::log-props req) {}))
        req-s (let [body (:body req)]
                (assoc
                  (f-util/stringify-keys
                    (-> req
                        (dissoc :body)
                        (update-in [:parameters :query] or-empty-map)
                        (update-in [:parameters :body] or-empty-map)
                        (update-in [:parameters :path] or-empty-map)
                        ))
                  "body"
                  body))
        context (.newInstance constructor (into-array Object [req-s
                                                              logger]))
        func-obj (.newInstance clz)
        param (coerse-body-to req
                              data-class
                              func-obj
                              get-decoder-method
                              )
        res (try
              (.invoke apply-method func-obj
                       (into-array Object [param context]))
              (catch Exception e {::exception e})
              )
        ]

    (cond
      (and (map? res) (::exception res))
      {:status 500 :headers {"content-type" "text/plain"}
       :body (exception-to-string (::exception res)) ::raw true}

      (and res (.isInstance meta-clz res))
      (do
        (let [^Iterable it res
              res-as-map (into {} (map
                                    (fn [^Map$Entry me] [(.getKey me) (.getValue me)])
                                    (-> it .iterator iterator-seq)))

              ]
          {:status  (get res-as-map "responseCode")
           :headers (merge (into {} (get res-as-map "headers"))
                           {"content-type" (get res-as-map "contentType")})
           :body    (get res-as-map "body")
           ::raw    true}))

      (instance? Node res)
      {:status  200
       :headers {"content-type" "text/xml"}
       ::raw    true
       :body    (fu/xml-to-utf-byte-array res)}

      :else
      (if-let [^Function encoder (.invoke get-encoder-method func-obj (object-array 0))]
        (.apply encoder res)
        (try
          (.writeValueAsString fu/jackson-json res)
          (catch Exception e {:status  500
                              ::raw    true
                              :headers {"content-type" "text/plain"}
                              :body    (exception-to-string e)}))))))

(defn- resolve-stuff
  "Given a ::jar-info and swagger, "
    [{:keys [::classloader]} swagger-info]

    (let [^String op-id (get swagger-info "operationId")
          clz (.loadClass ^ClassLoader classloader op-id)
          data-class (->>
                       (.getAnnotatedInterfaces clz)
                       (map #(.getType ^AnnotatedType %))
                       (filter #(instance? ParameterizedType %))
                       (filter #(.startsWith (.getTypeName ^ParameterizedType %) "funcatron.intf.Func<"))
                       (mapcat #(.getActualTypeArguments ^ParameterizedType %))
                       (filter #(instance? Class %))
                       first)
          ^Method apply-method (.getMethod clz "apply" (into-array Class [data-class
                                                                          (.loadClass ^ClassLoader classloader
                                                                                      "funcatron.intf.Context")]))
          ^Method get-encoder-method (.getMethod clz "jsonEncoder" (make-array Class 0))
          ^Method get-decoder-method (.getMethod clz "jsonDecoder" (make-array Class 0))
          ctx-clz (.loadClass ^ClassLoader classloader "funcatron.intf.impl.ContextImpl")
          meta-clz (.loadClass ^ClassLoader classloader "funcatron.intf.MetaResponse")
          ^Constructor constructor (first (.getConstructors ctx-clz))]

      (fn [req] (handle-actual-request
                  clz constructor apply-method
                  get-encoder-method get-decoder-method
                  data-class
                  meta-clz req))))

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
  (let [ring-req (f-util/make-ring-request message)
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
    ))

(defn- initialize-context
  "Okay... given a JAR and a classloader and such, we initialize the ContextImpl object.
  Return the context version and a function that releases the Context's resources"
  [{:keys [::classloader]} properties log-props]
  (let [classloader ^ClassLoader classloader
        context-impl-clz (.loadClass classloader "funcatron.intf.impl.ContextImpl")
        version (try
                  (let [meth (.getMethod context-impl-clz "getVersion" (make-array Class 0))]
                    (.invoke meth nil (make-array Object 0))
                    )
                  (catch Exception e (do (error e "Couldn't get version")
                                         "?")))
        end-life-method (.getMethod context-impl-clz "endLife" (make-array Class 0))
        init-method (.getMethod context-impl-clz "initContext" (into-array Class [Map ClassLoader Logger]))]
    (.invoke init-method nil (into-array Object [(fu/camel-stringify-keys properties) classloader (fu/logger-for log-props)]))

    [version (fn [] (.invoke end-life-method nil (make-array Object 0)))]
    )
  )

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
                          {:queue queue-name,
                           :host host,
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



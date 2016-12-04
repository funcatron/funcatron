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
  (:import (java.util.jar JarFile)
           (java.io File InputStream ByteArrayOutputStream ByteArrayInputStream)
           (java.net URLClassLoader URL)
           (java.lang.reflect Method Constructor InvocationTargetException AnnotatedType ParameterizedType)
           (funcatron.abstractions Router Router$Message)
           (org.apache.commons.io IOUtils)
           (java.util Base64 Map$Entry)
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
  [^Class clz ^Constructor constructor
   ^Method apply-method
   ^Method get-encoder-method
   ^Method get-decoder-method
   ^Class data-class
   logger
   ^Class meta-clz
   req]

  (let [
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
        context (.newInstance constructor (into-array Object [req-s logger]))
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
  "Given a ::jar-info and a "
    [{:keys [::classloader ::swagger]} req]

    (let [^String op-id (get req "operationId")
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
          logger (Logger/getLogger (str "Funcatron " (:host swagger) "/" (:basePath swagger) ))
          ctx-clz (.loadClass ^ClassLoader classloader "funcatron.intf.impl.ContextImpl")
          meta-clz (.loadClass ^ClassLoader classloader "funcatron.intf.MetaResponse")
          ^Constructor constructor (first (.getConstructors ctx-clz))]

      (fn [req] (handle-actual-request clz constructor apply-method
                                       get-encoder-method get-decoder-method
                                       data-class
                                       logger meta-clz req))))

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

(defn ^Router build-router
  "Take a JAR file (as a File or String) and create a Router"
  ([file] (build-router file true))
  ([file reply?]
   (let [the-jar (-> file jar-info-from-file update-jar-info-with-swagger)
         the-app (make-app the-jar)]
     (reify Router
       (routeMessage [this message]
         (route-to-jar message the-app reply?))))))



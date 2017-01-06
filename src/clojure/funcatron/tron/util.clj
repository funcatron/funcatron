(ns funcatron.tron.util
  "Utilities for Tron"
  (:require [cheshire.core :as json]
            [org.httpkit.server :as kit]
            [cognitect.transit :as transit]
            [io.sarnowski.swagger1st.context :as s1ctx]
            [camel-snake-kebab.core :as csk]
            [org.httpkit.client :as http]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]])
  (:import (cheshire.prettyprint CustomPrettyPrinter)
           (java.util Base64 Map Map$Entry List UUID Properties)
           (org.apache.commons.io IOUtils)
           (java.io InputStream ByteArrayInputStream ByteArrayOutputStream File FileInputStream OutputStreamWriter StringReader)
           (com.fasterxml.jackson.databind ObjectMapper)
           (javax.xml.parsers DocumentBuilderFactory)
           (org.w3c.dom Node)
           (javax.xml.transform TransformerFactory OutputKeys)
           (javax.xml.transform.dom DOMSource)
           (javax.xml.transform.stream StreamResult)
           (clojure.lang IFn)
           (funcatron.abstractions Router$Message)
           (java.security MessageDigest)
           (java.util.jar JarFile JarEntry)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)
           (java.util.function Function BiFunction)
           (funcatron.helpers Tuple2 Tuple3 LoggerBridge LoggerBridge$ActualLogger)
           (com.spotify.dns DnsSrvResolvers DnsSrvResolver LookupResult)
           (java.lang.management ManagementFactory ThreadMXBean RuntimeMXBean)
           (com.fasterxml.jackson.module.paramnames ParameterNamesModule)
           (com.fasterxml.jackson.datatype.jdk8 Jdk8Module)
           (com.fasterxml.jackson.datatype.jsr310 JavaTimeModule)
           (java.util.logging Logger LogRecord Level)
           (com.fasterxml.jackson.core JsonParser$Feature)))


(set! *warn-on-reflection* true)

(def ^ObjectMapper jackson-json
  "The Jackson JSON ObjectMapper"
  (let [jackson (->
                  (.findAndRegisterModules (ObjectMapper.))
                  (.registerModule (ParameterNamesModule.))
                  (.registerModule (Jdk8Module.))
                  (.registerModule (JavaTimeModule.))
                  )]
    (.configure jackson JsonParser$Feature/ALLOW_SINGLE_QUOTES true)
    jackson
    ))

(defn walk
  "Walk Clojure data structures and 'do the right thing'"
  [element-f key-f data]
  (let [m (element-f data)
        f (partial walk element-f key-f)]
    (cond

      (list? m) (map f m)
      (instance? List m) (mapv f m)
      (instance? Map m) (into {} (map (fn [^Map$Entry me]
                                        (let [k (.getKey me)]
                                          [(key-f k) (f (.getValue me))]
                                          )) (.entrySet ^Map m)))

      :else m
      )
    )
  )



(defn kwd-to-string
  "Converts a keyword to a String"
  [kw?]
  (if (keyword? kw?) (name kw?) kw?)
  )

(defn string-to-kwd
  "Converts a String to a Keyword"
  [s?]
  (if (string? s?) (keyword s?) s?))

(defn stringify-keys
  "Recursively transforms all map keys from keywords to strings."
  ([m] (stringify-keys identity m))
  ([f m] (walk f kwd-to-string m))
  )

(defn camel-stringify-keys
  "Recursively transforms all map keys from keywords to strings."
  ([m] (camel-stringify-keys identity m))
  ([f m] (walk f csk/->camelCaseString m))
  )

(defn keywordize-keys
  "Recursively transforms all map keys from keywords to strings."
  ([m] (keywordize-keys identity m))
  ([f m] (walk f string-to-kwd m))
  )

(defn kebab-keywordize-keys
  "Recursively transforms all map keys from keywords to strings."
  ([m] (kebab-keywordize-keys identity m))
  ([f m] (walk f csk/->kebab-case-keyword m))
  )

(defn ensure-json-clean
  "Return the value if it can be JSON serialized"
  [v]
  (if (or (nil? v)
          (instance? List v)
          (instance? Map v)
          (string? v)
          (instance? Number v)
          (keyword? v)
          (symbol? v)
          (instance? Boolean v)
          ) v nil))

(defn json-clean
  "Recursively make sure the data nodes are JSON serializable"
  [m]
  (walk ensure-json-clean identity m))

(def ^CustomPrettyPrinter pretty-printer
  "a JSON Pretty Printer"
  (json/create-pretty-printer json/default-pretty-print-options))

(defprotocol ToByteArray
  "Convert the incoming value to a byte array"
  (to-byte-array [v] "Convert v to a byte array and return the content type and byte array"))

(extend-type String
  ToByteArray
  (to-byte-array [^String v] ["text/plain" (.getBytes v "UTF-8")]))

(extend-type (Class/forName "[B")
  ToByteArray
  (to-byte-array [^"[B" v] ["application/octet-stream" v]))

(extend-type InputStream
  ToByteArray
  (to-byte-array [^InputStream v] ["application/octet-stream" (IOUtils/toByteArray v)]))

(extend-type Map
  ToByteArray
  (to-byte-array [^Map v] ["application/json" (second (to-byte-array (json/generate-string v)))]))

(extend-type Node
  ToByteArray
  (to-byte-array [^Node n]
    (let [transformer (.newTransformer (TransformerFactory/newInstance))]
      (.setOutputProperty transformer OutputKeys/INDENT "yes")
      (let [source (DOMSource. n)
            out (ByteArrayOutputStream.)]
        (.transform transformer source (StreamResult. out))
        ["text/xml" (.toByteArray out)]))))

(defn ^String encode-body
  "Base64 Encode the body's bytes. Pass data to `to-byte-array` and Base64 encode the result "
  [data]
  (.encodeToString (Base64/getEncoder) (second (to-byte-array data)))
  )

(defmulti fix-payload "Converts a byte-array body into something full by content type"
          (fn [x & _]
            (let [^String s (if (nil? x)
                              "application/octet-stream"
                              (-> x
                                  (clojure.string/split #"[^a-zA-Z+\-/0-9]")
                                  first
                                  ))]
              (.toLowerCase
                s))))

(defmethod fix-payload "application/json"
  [content-type ^"[B" bytes]
  (keywordize-keys (.readValue jackson-json bytes Map)))

(defmethod fix-payload "text/plain"
  [content-type ^"[B" bytes]
  (String. bytes "UTF-8")
  )

(defmethod fix-payload "text/xml"
  [content-type ^"[B" bytes]
  (let [db-factory (DocumentBuilderFactory/newInstance)
        db (.newDocumentBuilder db-factory)
        ret (-> (.parse db (ByteArrayInputStream. bytes))
                .getDocumentElement)
        ]
    (.normalize ret)
    ret
    )
  )

(defmethod fix-payload :default
  [_ ^"[B" bytes]
  bytes)

(defmethod fix-payload "application/octet-stream"
  [_ ^"[B" bytes]
  bytes)

(defn ^java.util.function.Function promote-to-function
  "Promotes a Clojure IFn to a Java function"
  [f]
  (reify java.util.function.Function
    (apply [this v] (f v))))


(defprotocol FuncMaker "Make a Java function out of stuff"
  (^java.util.function.Function to-java-function [v] "Convert the incoming thing to a Java Function"))

(extend-type IFn
  FuncMaker
  (^java.util.function.Function to-java-function [^IFn f]
    (reify java.util.function.Function
      (apply [this param] (f param))
      )))

(extend-type java.util.function.Function
  FuncMaker
  (^java.util.function.Function to-java-function [^java.util.function.Function f] f))

(defn as-int
  "Take anything and try to convert it to an int"
  ([s] (as-int s 0))
  ([s default]
   (try
     (cond
       (nil? s) default
       (string? s) (read-string s)
       (int? s) s
       (instance? Number s) (.longValue ^Number s)
       :else default
       )

     (catch Exception e default))
    ))

(defn make-ring-request
  "Takes normalized request and turns it into a Ring style request"
  [^Router$Message req]
  (let [headers (get (.body req) "headers")
        body (get (.body req) "body")
        body (cond
               (nil? body) nil
               (string? body) (ByteArrayInputStream. (.getBytes ^String body "UTF-8"))
               (bytes? body) (ByteArrayInputStream. ^"[B" body)

               :else nil
               )

        ret {:server-port    (.port req)
             :server-name    (.host req)
             :remote-addr    (.remoteAddr req)
             :uri            (.uri req)
             :router-message req
             :query-string   (.args req)
             :scheme         (.scheme req)
             :request-method (.toLowerCase ^String (or (.method req) "GET"))
             :protocol       (.protocol req)
             :headers        headers
             :body           body
             }
        ]
    ret
    )
  )

(defn some-when
  "Return the element if the element passed to the function returns true"
  [element function]
  (if (function element) element nil))


(defn ^File new-file
  "Create a new File object."
  [^File base ^String name]
  (File. base name))

(defn runner-mode?
  "Calculates if we're in Dev mode from options"
  [opts]
  (boolean (-> opts :options :runner)))

(defn ^IFn start-http-server
  "Starts a http-kit server with the options specified in command line options. `function` is the Ring handler.
  Returns the Stop Server function"
  [opts function]
  (kit/run-server
    function
    {:max-body (* 256 1024 1024)                            ;; 256mb max size
     :port     (or
                 (-> opts :options :web_port)
                 (if (runner-mode? opts) 4000 3000))}))

(defprotocol CalcSha256
  "Calculate the SHA for something"
  (sha256 [file]))

(extend InputStream
  CalcSha256
  {:sha256 (fn [^InputStream is]
             (let [md (MessageDigest/getInstance "SHA-256")
                   ba (byte-array 1024)]
               (loop []
                 (let [cnt (.read is ba)]
                   (when (>= cnt 0)
                     (if (> cnt 0) (.update md ba 0 cnt))
                     (recur)
                     )))
               (.close is)
               (.digest md)))})

(extend File
  CalcSha256
  {:sha256 (fn [^File file]
             (sha256 (FileInputStream. file))
             )})

(extend String
  CalcSha256
  {:sha256 (fn [^String str]
             (sha256 (.getBytes str "UTF-8"))
             )})

(extend (Class/forName "[B")
  CalcSha256
  {:sha256 (fn [^"[B" bytes]
             (sha256 (ByteArrayInputStream. bytes))
             )})

(defprotocol ABase64Encoder
  (base64encode [what]))

(extend (Class/forName "[B")
  ABase64Encoder
  {:base64encode (fn [^"[B" bytes] (.encodeToString (Base64/getEncoder) bytes))})

(extend String
  ABase64Encoder
  {:base64encode (fn [^String bytes] (base64encode (.getBytes bytes "UTF-8")))})

(extend nil
  ABase64Encoder
  {:base64encode (fn [the-nil] "")})

(extend InputStream
  ABase64Encoder
  {:base64encode (fn [^InputStream is] (base64encode (second (to-byte-array is))))})

(def funcatron-file-regex
  #"(?:.*\/|^)funcatron\.(json|yaml)$")

(defn- funcatron-file-type
  "Is the entry a Funcatron definition file"
  [^JarEntry jar-entry]
  (second (re-matches funcatron-file-regex (.getName jar-entry))))

(def ^:private file-type-mapping
  {"yaml" :yaml
   "yml"  :yaml
   "json" :json})

(defn get-swagger-from-jar
  "Take a JarFile and get the swagger definition"
  [^JarFile jar]
  (let [entries (-> jar .entries enumeration-seq)
        entries (map (fn [x] {:e x :type (funcatron-file-type x)}) entries)
        entries (filter :type entries)
        entries (mapcat (fn [{:keys [e type]}]
                          (try
                            [(s1ctx/load-swagger-definition
                               (file-type-mapping type)
                               (slurp (.getInputStream jar e))
                               )]
                            (catch Exception e
                              (do
                                (error e "Failed to get Swagger information")
                                nil)
                              )
                            )
                          ) entries)
        ]
    (first entries)
    )
  )

(defn find-swagger-info
  "Tries to figure out the file type and then comb through it to figure out the Swagger file and the file type"
  [^File file]
  (or
    (try
      (let [ret (get-swagger-from-jar (JarFile. file))]
        (if ret
          {:type :jar :swagger (keywordize-keys ret)}
          (.delete file)))
      (catch Exception e nil))))


(defn ^"[B" transit-encode
  "Takes an object and transit-encodes the object. Returns a byte array"
  [obj]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer obj)
    (.toByteArray out)))

(defn transit-decode
  "Takes a byte array and returns the transit decoded object"
  [^"[B" bytes]
  (let [in (ByteArrayInputStream. bytes)
        reader (transit/reader in :json)]
    (transit/read reader)))

;; FIXME -- hardcode 50 threads... enough?
(defonce ^ScheduledExecutorService ^:private thread-pool (Executors/newScheduledThreadPool 50))

(defn run-in-pool
  "Runs a function (IFn, Callable, Runnable) in the threadpool"
  [func]
  (cond
    (instance? Runnable func)
    (.submit thread-pool ^Runnable func)

    (instance? Callable func)
    (.submit thread-pool ^Callable func)

    :else
    (throw (Exception. (str "The class " (.getClass ^Object func) " is neither Callable nor Runnable")))))

(defn run-after
  "Runs a function (IFn, Callable, Runnable) in the threadpool in `delay` milliseconds"
  [func ^long delay]
  (cond
    (instance? Runnable func)
    (.schedule thread-pool ^Runnable func delay TimeUnit/MILLISECONDS)

    (instance? Callable func)
    (.schedule thread-pool ^Callable func delay TimeUnit/MILLISECONDS)

    :else
    (throw (Exception. (str "The class " (.getClass ^Object func) " is neither Callable nor Runnable"))))
  )

(defprotocol ToClojureFunc
  "Converts a Function into a Clojure Function"
  (to-clj-func [f]))

(extend nil
  ToClojureFunc
  {:to-clj-func (fn [_] (fn [& _]))})

(extend IFn
  ToClojureFunc
  {:to-clj-func (fn [^IFn c] c)}
  )

(extend Function
  ToClojureFunc
  {:to-clj-func (fn [^Function c] (fn [a & _] (.apply c a)))})

(extend Callable
  ToClojureFunc
  {:to-clj-func (fn [^Callable c] (fn [& _] (.call c)))})

(extend Runnable
  ToClojureFunc
  {:to-clj-func (fn [^Runnable c] (fn [& _] (.run c)))})

(defprotocol ToJavaFunction
  "Converts a wide variety of stuff into a Java function"
  (to-java-fn [f]))

(extend nil
  ToJavaFunction
  {:to-java-fn (fn [_] (reify Function (apply [this _] nil)))})

(extend IFn
  ToJavaFunction
  {:to-java-fn (fn [^IFn f] (reify Function (apply [this a] (f a))))})

(extend Function
  ToJavaFunction
  {:to-java-fn (fn [^Function f] f)})

(extend Callable
  ToJavaFunction
  {:to-java-fn (fn [^Callable f] (reify Function (apply [this a] (.call f))))})

(extend Runnable
  ToJavaFunction
  {:to-java-fn (fn [^Runnable f] (reify Function (apply [this a] (.run f))))})

(defprotocol TupleUtil
  (from-tuple [this])
  (to-tuple [this]))

(extend List
  TupleUtil
  {:from-tuple (fn [this] this)
   :to-tuple   (fn [lst] (if (< 2 (count lst))
                           (Tuple2. (first lst) (second lst))
                           (let [[a b c] lst] (Tuple3. a b c))))})

(extend nil
  TupleUtil
  {:from-tuple (fn [this] this)
   :to-tuple   (fn [lst] (Tuple2. nil nil))})

(extend Tuple2
  {:from-tuple (fn [^Tuple2 this] (.toList this))
   :to-tuple   (fn [tuple] tuple)})

(defn ^String random-uuid
  "Create a Random UUID string via Java's UUID class"
  []
  (.toString (UUID/randomUUID)))

(defn in-mesos?
  "Returns true if we're running in mesos"
  []
  (boolean (get (System/getenv) "MARATHON_APP_ID"))
  )

(defn graceful-exit
  "Gracefully exit the process. This means on Mesos, telling Mesos to terminate us"
  [code]

  (info (str "Graceful shutdown with code " code)) 2

  (when-let [mesos-app (get (System/getenv) "MARATHON_APP_ID")]
    (info "We're in Mesos-land, so tell leader.mesos:8080 to delete us")
    @(http/delete
       (str "http://leader.mesos:8080/v2/apps" mesos-app)))

  (System/exit code)
  )

(defn ^:private ^DnsSrvResolver dns-resolver
  "Create a DNS resolver"
  []
  (-> (DnsSrvResolvers/newBuilder)
      (.cachingLookups true)
      (.dnsLookupTimeoutMillis 1000)
      (.retentionDurationMillis 100)
      .build))

(defn dns-lookup
  "Look up the SRV DNS records"
  [name]
  (try
    (mapv
      (fn [^LookupResult r]
        {:host     (.host r)
         :port     (.port r)
         :priority (.priority r)
         :weight   (.weight r)
         :ttl      (.ttl r)})
      (.resolve (dns-resolver) name))
    (catch Exception e
      (do (error e (str "Failed DNS lookup for " name))
          []))))

(def ^:private ^ThreadMXBean thread-mbx (ManagementFactory/getThreadMXBean))

(defn time-execution
  "Takes a function. Runs the function and returns the functions return value along with timing information."
  [f]
  (let [current-cpu-time (.getCurrentThreadCpuTime thread-mbx)
        current-user-time (System/nanoTime)
        ret
        (try
          {:value (f)}
          (catch Exception e {:exception e}))
        cpu (- (.getCurrentThreadCpuTime thread-mbx) current-cpu-time)
        clock (- (System/nanoTime) current-user-time)
        ]
    (merge ret
           {:thread-cpu-nano   cpu
            :thread-clock-nano clock
            :thread-cpu-sec    (/ cpu 1000000000.0)
            :thread-clock-sec  (/ clock 1000000000.0)
            })))

(defn tron-mode?
  "Calculates if we're in Tron mode from options"
  [opts]
  (boolean (-> opts :options :tron)))

(defn dev-mode?
  "Calculates if we're in Dev mode from options"
  [opts]
  (boolean (-> opts :options :devmode)))


(defn compute-host-and-port
  "Computes the hostname and port -- FIXME this should be pluggable for Mesos deploys and such"
  [opts]
  (let [port (if (runner-mode? opts) 4000 3000)]
    {:host
     (or
       (and
         (get (System/getenv) "MESOS_CONTAINER_NAME")
         (get (System/getenv) "HOST"))

       (-> opts :options :web_host)

       "localhost")
     :port
     (or
       (try
         (and
           (get (System/getenv) "MESOS_CONTAINER_NAME")
           (let [x (read-string (get (System/getenv) (str "PORT_" port)))]
             (if (integer? x) x nil)
             ))
         (catch Exception _ nil))

       (-> opts :options :web_port)

       port)})
  )

(defn square-numbers
  "Takes a map. For each key that has a value that's a number, create key-sq with the squared number.
  For non-number values, drop them"
  [m]
  (into {}
        (mapcat
          (fn [[k v]]
            (if (number? v)
              (let [the-ns (if (keyword? k) (namespace k) nil)
                    kwd (fn [x] (keyword the-ns x))]
                [[k v] [(-> k name (str "-sq") kwd) (* v v)]])
              nil))
          m)))

(defn ^"[B" xml-to-utf-byte-array
  "Take XML and convert it into UTF-8 encoded byte array"
  [^Node node]
  (let [dom-source (DOMSource. node)
        bos (ByteArrayOutputStream.)
        out (OutputStreamWriter. bos)
        transformer (-> (TransformerFactory/newInstance) .newTransformer)]
    (.setOutputProperty transformer OutputKeys/INDENT "yes")
    (.setOutputProperty transformer OutputKeys/ENCODING "UTF-8")
    (.transform transformer dom-source (StreamResult. out))
    (.close out)
    (.toByteArray bos)))

(defn clean-sha
  "Take a base-64 encoded sha and turn it into a valid RabbitMQ name"
  [^String s]
  (clojure.string/join (take 16 (filter #(Character/isJavaLetterOrDigit %) s))))

(defn route-to-sha
  "Get a SHA for the route"
  [host path]
  (-> (str host ";" path)
      sha256
      base64encode
      clean-sha))


(defn string-to-properties
  "Read a String as Java properties format and then kebab-keywordize it"
  [s]
  (let [p (Properties.)
        sr (StringReader. s)]
    (.load p sr)
    (kebab-keywordize-keys p)))

(defn ^String file-from-classloader
  "Given a classloader and a filename, return the String contents of the file, if available"
  [^ClassLoader cl ^String name]
  (some-> cl
          (.getResource name)
          slurp)
  )

(defn manifest-from-classloader
  "Pass in a classloader and get the Manifest out"
  [^ClassLoader cl]
  (or
    (some->
      (file-from-classloader cl "META-INF/MANIFEST.MF")
      string-to-properties)
    {}
    ))

(defn git-sha-txt
  "Given a classloader, try to slurp the gitsha.txt file and return its contents"
  [^ClassLoader cl]
  (some->
    (file-from-classloader cl "gitsha.txt")
    clojure.string/split-lines
    first
    clojure.string/trim
    ))

(defn first-matching-key
  "Given a map, find the first matching key in the collection"
  [m & potential]
  (when (map? m)
    (loop [x potential]
      (let [[f & r] x]
        (cond
          (contains? m f)
          (get m f)

          (not (empty? r))
          (recur r))))))

(defn git-sha-from-classloader
  "Try a bunch of strategies to get the git sha from the classloader"
  [^ClassLoader cl]
  (or
    (git-sha-txt cl)
    (some-> (manifest-from-classloader cl)
            (first-matching-key :git-head-rev :git-describe)))
  )

(defn version-info-from-classloader
  "Pass in a classloader and get various version information into a map"
  [^ClassLoader cl]
  (let [sha (some->> (git-sha-from-classloader cl) (assoc {} :git-sha))]
    (merge sha)))

(def log-level-mapping
  {Level/SEVERE  :error
   Level/WARNING :warn
   Level/INFO    :info
   Level/CONFIG  :debug
   Level/FINE    :debug
   Level/FINER   :trace
   Level/FINEST  :trace})

(defn- build-line-number-from
  "Take a map of properties about the execution environment and build
  the Java Logger's 'line number' which will be various SHA information."
  [props]
  (clojure.string/join
    (or (:line-sep props) "&")
    (remove
      nil?
      [(some->> (or (:git-sha props)
                    (:sha props))
                (str "GIT:"))

       (some->> (:reply-to props)
                (str "REQ:"))

       (some->> (:line-extra props)
                (str "XTR:"))
       ])))

(defn- hostname-from-props
  "Compute the hostname from properties"
  [props]

  (str (some->
         (:host props)
         (str ":"))
       (or (:basePath props)
           "funcatron")
       )
  )

(defn- do-logging-via-timbre
  "Log the data via timbre"
  [name config props ^LogRecord record]
  (let [level (log-level-mapping (.getLevel record))]
    (when
      (timbre/may-log? level name config)

      (timbre/-log! config level (.getSourceClassName record)
                    (.getSourceMethodName record)
                    (build-line-number-from props) :p (.getThrown record)
                    (delay (into [(.getMessage record)] (.getParameters record))) nil))

    ))

(defn ^Logger logger-for
  "Create a java.util.Logger instance that actually sends log events to Timbre.
  Properties we care about :queue, :host, :basePath, :sha, :reply-to, :host-extra, :line-extra, :line-sep"
  [log-props]
  (let [name  (or (:queue log-props) "funcatron")
        d-hostname (delay (str (hostname-from-props log-props) (:host-extra log-props)))

        t-config (-> timbre/*config*
                     (assoc :middleware [(fn [d]
                                           (merge d {:hostname_
                                                     d-hostname}))]))]
    (LoggerBridge.
      (reify LoggerBridge$ActualLogger
        (isLoggable [_ lvl]
          (let [level (log-level-mapping lvl)]
            (timbre/may-log? level name t-config)))
        (^void log [_ ^LogRecord record]
          (do-logging-via-timbre name t-config log-props record))))))

(defn restore-body
  "Okay... if we've preserved the body, restore it"
  [req]
  (if-let [bytes (:body-bytes req)]
    (assoc req :body (ByteArrayInputStream. bytes))
    req))

(defn- -preserve-body
  "The actual handler that preserves the body"
  [handler req]
  (let [req (if (instance? InputStream (:body req))
              (let [ba (second (to-byte-array (:body req)))]
                (-> req
                    (assoc :body-bytes ba)
                    (assoc :body (ByteArrayInputStream. ba))))
              req)]
    (handler req)))

(defn preserve-body
  "So, sometimes Ring middleware is destructive, which sucks... so
  if there's a body field and it's a stream, slurp the bytes and allow
  the stream to be restored"
  [handler]
  (fn [req]
    (-preserve-body handler req)
    )
  )

(def ^Function jackson-serializer
  "A Java function that serializes any object to a byte array"
  (reify Function
    (apply [_ o] (.writeValueAsBytes jackson-json o))))

(def ^BiFunction jackson-deserializer
  "A Java BiFunction that takes an InputStream and target class and returns a Jackson read-type"
  (reify BiFunction
    (apply [_ is clz] (.readValue jackson-json ^InputStream is ^Class clz))))
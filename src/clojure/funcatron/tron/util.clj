(ns funcatron.tron.util
  "Utilities for Tron"
  (:require [cheshire.core :as json]
            [clojure.spec :as s]
            [org.httpkit.server :as kit]
            [funcatron.tron.options :as the-opts]
            [cognitect.transit :as transit]
            [io.sarnowski.swagger1st.context :as s1ctx]
            [clojure.tools.logging :as log])
  (:import (cheshire.prettyprint CustomPrettyPrinter)
           (java.util Base64 Map Map$Entry List)
           (org.apache.commons.io IOUtils)
           (java.io InputStream ByteArrayInputStream ByteArrayOutputStream File FileInputStream)
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
           (java.util.concurrent Executors ExecutorService)))


(set! *warn-on-reflection* true)

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

(defn keywordize-keys
  "Recursively transforms all map keys from keywords to strings."
  ([m] (keywordize-keys identity m))
  ([f m] (walk f string-to-kwd m))
  )



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
  (let [jackson (ObjectMapper.)
        info1 (keywordize-keys (.readValue jackson bytes Map))
        ]
    info1))

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
  (let [headers (keywordize-keys (get (.body req) "headers"))
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

(defn run-server
  "Starts a http-kit server with the options specified in command line options. `function` is the Ring handler. `the-atom` is where to put the 'stop the server' function."
  [function the-atom]
  (reset! the-atom
          (kit/run-server function {:max-body (* 256 1024 1024) ;; 256mb max size

                                    :port (or
                                            (-> @the-opts/command-line-options :options :web_port)
                                            3000)})))

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

(def funcatron-file-regex
  #"(?:.*\/|^)funcatron\.(json|yml|yaml)$")

(defn- funcatron-file-type
  "Is the entry a Funcatron definition file"
  [^JarEntry jar-entry]
  (second (re-matches funcatron-file-regex (.getName jar-entry))))

(def ^:private file-type-mapping
  {"yaml" :yaml
   "yml" :yaml
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
                                (log/error e "Failed to get Swagger information")
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
    (try (let [ret (get-swagger-from-jar (JarFile. file))]
           (println "Ret is " ret)
           {:type :jar :swagger (keywordize-keys ret)})
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

(defonce ^ExecutorService ^:private thread-pool (Executors/newCachedThreadPool))

(defn run-in-pool
  [func]
  (cond
    (instance? Runnable func)
    (.submit thread-pool ^Runnable func)

    (instance? Callable func)
    (.submit thread-pool ^Callable func)

    :else
    (throw (Exception. (str "The class " (.getClass ^Object func) " is neither Callable nor Runnable")))))
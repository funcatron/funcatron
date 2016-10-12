(ns funcatron.tron.util
  "Utilities for Tron"
  (:require [cheshire.core :as json]
            [clojure.spec :as s])
  (:import (cheshire.prettyprint CustomPrettyPrinter)
           (java.util Base64 Map Map$Entry List)
           (org.apache.commons.io IOUtils)
           (java.io InputStream ByteArrayInputStream ByteArrayOutputStream)
           (com.fasterxml.jackson.databind ObjectMapper)
           (javax.xml.parsers DocumentBuilderFactory)
           (org.w3c.dom Node)
           (javax.xml.transform TransformerFactory OutputKeys)
           (javax.xml.transform.dom DOMSource)
           (javax.xml.transform.stream StreamResult)
           (clojure.lang IFn)
           (funcatron.abstractions Router$Message)))


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
  (^"[B" to-byte-array [v] "Convert v to a byte array and return the content type and byte array"))

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

(defn encode-body
  "Base64 Encode the body's bytes. Pass data to `to-byte-array` and Base64 encode the result "
  [data]
  (.encodeToString (Base64/getEncoder) (to-byte-array data))
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
         :request-method (.toLowerCase ^String (.method req))
         :protocol       (.protocol req)
         :headers        headers
         :body           body
         }
        ]
    (println "Ring req " ret)
    ret
    )
  )

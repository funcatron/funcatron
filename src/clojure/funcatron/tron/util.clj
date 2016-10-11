(ns funcatron.tron.util
  "Utilities for Tron"
  (:require [cheshire.core :as json]
            [clojure.spec :as s]
            [funcatron.tron.walk :as walk])
  (:import (cheshire.prettyprint CustomPrettyPrinter)
           (java.util Base64 Map)
           (org.apache.commons.io IOUtils)
           (java.io InputStream ByteArrayInputStream ByteArrayOutputStream)
           (com.fasterxml.jackson.databind ObjectMapper)
           (javax.xml.parsers DocumentBuilderFactory)
           (org.w3c.dom Node)
           (javax.xml.transform TransformerFactory OutputKeys)
           (javax.xml.transform.dom DOMSource)
           (javax.xml.transform.stream StreamResult)
           (clojure.lang IFn)))


(set! *warn-on-reflection* true)

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
        info1 (walk/keywordize-keys (.readValue jackson bytes Map))
        body (if
               (and (true? (:body_base64_encoded info1))
                    (:body info1)
                    (< 0 (count (:body info1)))
                    )
               (fix-payload (:content_type info1) (.decode (Base64/getDecoder) ^String (:body info1)))

               (:body info1)
               )
        ]
    (assoc info1 :body body)))

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
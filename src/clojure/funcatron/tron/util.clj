(ns funcatron.tron.util
  "Utilities for Tron"
  (:require [cheshire.core :as json]
            [clojure.spec :as s])
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
           (java.util.function Function)
           (clojure.lang IFn)))


(set! *warn-on-reflection* true)

(def ^CustomPrettyPrinter pretty-printer
  "a JSON Pretty Printer"
  (json/create-pretty-printer json/default-pretty-print-options))

(defprotocol ToByteArray
  "Convert the incoming value to a byte array"
  (^"[B" to-byte-array [v] "Convert v to a byte array"))

(extend-type String
  ToByteArray
  (to-byte-array [^String v] (.getBytes v "UTF-8")))

(extend-type (Class/forName "[B")
  ToByteArray
  (to-byte-array [^"[B" v] v))

(extend-type InputStream
  ToByteArray
  (to-byte-array [^InputStream v] (IOUtils/toByteArray v)))

(extend-type Map
  ToByteArray
  (to-byte-array [^Map v] (to-byte-array (json/generate-string v))))

(extend-type Node
  ToByteArray
  (to-byte-array [^Node n]
    (let [transformer (.newTransformer (TransformerFactory/newInstance))]
      (.setOutputProperty transformer OutputKeys/INDENT "yes")
      (let [source (DOMSource. n)
            out (ByteArrayOutputStream.)]
        (.transform transformer source (StreamResult. out))
        (.toByteArray out)))))

(defn encode-body
  "Base64 Encode the body's bytes. Pass data to `to-byte-array` and Base64 encode the result "
  [data]
  (.encodeToString (Base64/getEncoder) (to-byte-array data))
  )

(defmulti fix-payload "Converts a byte-array body into something full by content type"
          (fn [x & _]
            (let [^String s (if (nil? x)
                              "text/plain"
                              (-> x
                                  (clojure.string/split #"[^a-zA-Z+\-/0-9]")
                                  first
                                  ))]
              (.toLowerCase
                s))))

(defmethod fix-payload "application/json"
  [content-type ^"[B" bytes]
  (let [jackson (ObjectMapper.)]
    (.readValue jackson bytes Map)
    )
  )

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

(defn ^Function promote-to-function
  "Promotes a Clojure IFn to a Java function"
  [f]
  (reify Function
    (apply [this v] (f v))))


(defprotocol FuncMaker "Make a Java function out of stuff"
  (^Function to-java-function [v] "Convert the incoming thing to a Java Function"))

(extend-type IFn
  FuncMaker
  (^Function to-java-function [^IFn f]
    (reify Function
      (apply [this param] (f param))
      )))

(extend-type Function
  FuncMaker
  (^Function to-java-function [^Function f] f))
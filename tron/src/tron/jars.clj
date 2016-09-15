(ns tron.jars
  (:require [io.sarnowski.swagger1st.context :as s1ctx])
  (:import (java.io File ByteArrayOutputStream EOFException)
           (java.net URLClassLoader URL)
           (java.util.jar JarFile JarEntry)
           (java.util UUID)))

(set! *warn-on-reflection* true)

(defn jar-info-from-file
  "Take a file object and turn it into a combo classloader and JarFile"
  [item]
  (let [file (clojure.java.io/file item)]
    {:jar         (JarFile. file)
     :classloader (URLClassLoader. (into-array URL [(-> file .toURI .toURL)]) nil nil)
     :uuid        (.toString (UUID/randomUUID))
     }
    ))

(def funcatron-file-regex
  #"(?:.*\/|^)funcatron\.(json|yml|yaml)$")

(defn- funcatron-file-type
  [^JarEntry jar-entry]
  (second (re-matches funcatron-file-regex (.getName jar-entry))))

(def ^:private file-type-mapping
  {"yaml" :yaml
   "yml" :yaml
   "json" :json})

(defn get-swagger
  "Take the output from jar-info-from-file and get the swagger definition"
  [jar-info]
  (let [^JarFile jar (:jar jar-info)
        entries (-> jar .entries enumeration-seq)
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
                                (println e)                 ;; FIXME log exception
                                nil)
                                   )
                            )
                          ) entries)
        ]
    (first entries)
    )
  )

(defn update-jar-info-with-swagger
  "Takes the jar-info and adds the parsed swagger information"
  [jar-info]
  (let [swagger (get-swagger jar-info)]
    (assoc jar-info :swagger swagger)
    ))

(defn qq
  []
  (->  "resources/test.jar"
       jar-info-from-file
       update-jar-info-with-swagger))

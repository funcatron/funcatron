(ns funcatron.tron.modes.common
  (:require [funcatron.tron.util :as fu]
            [funcatron.tron.options :as opts]
            [funcatron.tron.brokers.shared :as shared-b])
  (:import (java.io File)))

(defn ^File calc-storage-directory
  "Compute the storage directory without reference to the global opts"
  [opts]
  (let [^String the-dir (or (-> opts :options :bundle_store)
                            (str (get (System/getProperties) "user.home")
                                 (get (System/getProperties) "file.separator")
                                 "funcatron_bundles"
                                 ))
        the-dir (File. the-dir)
        ]
    (.mkdirs the-dir)
    the-dir)
  )

(defn sha-for-file
  "Get the Sha256 and swagger information for the file"
  [file]
  (let [sha (fu/sha256 file)
        sha (fu/base64encode sha)]
    (when sha
      {:sha       sha
       :type      :jar})
    ))


#_(defn get-bundle-info
  "Returns the sha info and the swagger info for the file if it's a valid Func Bundle"
  [^File file]
  (let [{:keys [sha type swagger file-info] :as info} (sha-and-swagger-for-file file)]
    (if (and type file-info (:basePath swagger))
      [sha {:file file :swagger swagger :info info}])))

(defn load-func-bundles
  "Reads the func bundles from the storage directory"
  [storage-directory]
  (let [dir ^File storage-directory
        files (filter #(and (.isFile ^File %)
                            (.endsWith (.getName ^File %) ".funcbundle"))
                      (.listFiles dir))]
    (into {}
          (filter
            boolean
            (for [file files] (sha-for-file file))))))

(defn tron-queue
  "Compute the name of the tron queue"
  []

  (or
    (-> opts/command-line-options deref :options :tron_queue)
    "for_tron"))


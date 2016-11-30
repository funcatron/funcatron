(ns funcatron.tron.modes.common
  (:require [funcatron.tron.util :as fu]
            [funcatron.tron.options :as f-opts]
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

(defn sha-and-swagger-for-file
  "Get the Sha256 and swagger information for the file"
  [file]
  (let [sha (fu/sha256 file)
        sha (fu/base64encode sha)

        {:keys [type, swagger] {:keys [host, basePath]} :swagger :as file-info}
        (fu/find-swagger-info file)]
    {:sha sha
     :type type
     :swagger swagger
     :host host
     :basePath basePath
     :file-info file-info}
    ))


(defn get-bundle-info
  "Returns the sha info and the swagger info for the file if it's a valid Func Bundle"
  [^File file]
  (let [{:keys [sha type swagger file-info]} (sha-and-swagger-for-file file)]
    (if (and type file-info)
      [sha {:file file :swagger swagger}])))

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
            (for [file files] (get-bundle-info file))))))


(defn connect-to-message-queue
  "Connect to the message queue and deal with messages via `handler`"
  [opts ^String listen-to handler]
  (let [queue (shared-b/wire-up-queue opts)
        end-func
        (.listenToQueue
          queue
          listen-to
          (fu/promote-to-function
            (fn [msg]
              (fu/run-in-pool (fn [] (handler msg))))))]

    {::queue queue
     ::end-func end-func}

    )
  )


(defn tron-queue
  "Compute the name of the tron queue"
  []
  ;; FIXME compute the name of the Tron queue
  (or "tron"))
(ns funcatron.tron.modes.common
  (:require [funcatron.tron.util :as fu]
            [funcatron.tron.options :as f-opts]
            [funcatron.tron.brokers.shared :as shared-b])
  (:import (java.io File)))

(def storage-directory
  (delay
    (let [^String the-dir (or (-> @f-opts/command-line-options :options :bundle_store)
                              (str (get (System/getProperties) "user.home")
                                   (get (System/getProperties) "file.separator")
                                   "funcatron_bundles"
                                   ))
          the-dir (File. the-dir)
          ]
      (.mkdirs the-dir)
      the-dir)))

(defn sha-and-swagger-for-file
  "Get the Sha256 and swagger information for the file"
  [file]
  (let [sha (fu/sha256 file)
        sha (fu/base64encode sha)

        {:keys [type, swagger] {:keys [host, basePath]} :swagger :as file-info}
        (fu/find-swagger-info file)]
    {:sha sha :type type :swagger swagger :host host :basePath basePath :file-info file-info}
    ))

(defn load-func-bundles
  "Reads the func bundles from the storage directory"
  [storage-directory func-bundles]
  (let [dir ^File storage-directory
        files (filter #(and (.isFile ^File %)
                            (.endsWith (.getName ^File %) ".funcbundle"))
                      (.listFiles dir))]
    (doseq [file files]
      (let [{:keys [sha type swagger file-info]} (sha-and-swagger-for-file file)]
        (if (and type file-info)
          (swap! func-bundles assoc sha {:file file :swagger swagger}))))))


(defn connect-to-message-queue
  "Connect to the message queue and put the queue information in the atom in `message-queue`
  and deal with messages via `handler`"
  [message-queue ^String listen-to handler]
  (let [queue (shared-b/wire-up-queue)]
    (when message-queue
      (reset! message-queue queue))
    (.listenToQueue queue listen-to
                    (fu/promote-to-function
                      (fn [msg]
                        (fu/run-in-pool (fn [] (handler msg))))))
    )
  )



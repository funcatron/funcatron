(ns funcatron.tron.modes.common
  (:require [funcatron.tron.util :as fu]
            [funcatron.tron.options :as opts]
            [funcatron.tron.brokers.shared :as shared-b])
  (:import (java.io File)
           (com.timgroup.statsd StatsDClient NonBlockingStatsDClient)))

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

(def statsd-atom (atom {:enabled false :client nil}))

(defn update-statsd
  "Takes a message that might include information on updating statsd and
  updates is necessary"
  [{:keys [set_statsd] :as msg} uuid]
  (when (true? set_statsd)
    (let [{:keys [enabled host port]} (:statsd msg)]
      (cond
        (and (true? enabled)
             (string? host)
             (number? port))
        (let [-host host
              -port port]
          (swap! statsd-atom
                 (fn [{:keys [host port enabled] :as blob}]
                   ;; host and port have not changed
                   (if
                     (and (true? enabled)
                          (= host -host)
                          (= port -port))
                     blob

                     ;; rebuild the atom
                     (try
                       (let [the-info (str "runner." uuid)
                             the-client (NonBlockingStatsDClient. the-info -host -port)
                             new-blob
                             {:enabled true
                              :host    -host
                              :port    -port
                              :client  the-client}]
                         (println "New blob " new-blob)
                         new-blob
                         )
                       (catch Exception e (do (println "Failed ") (println e) blob))
                       )
                     ))))

        (false? enabled)
        (reset! statsd-atom {:enabled false :client nil})

        :else
        nil
        )
      )))

(defn ^StatsDClient statsd-client
  "Get the StatsD client from the atom"
  []
  (let [{:keys [enabled client]} @statsd-atom]
    (if (and enabled client) client
                             nil)
    ))
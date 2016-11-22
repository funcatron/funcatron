(ns funcatron.tron.modes.tron-mode
  (:require [funcatron.tron.util :as fu]
            [clojure.java.io :as cio]
            [clojure.tools.logging :as log]
            [ring.middleware.json :as rm-json :refer [wrap-json-response]]
            [compojure.core :refer [GET defroutes POST]]
            [funcatron.tron.brokers.shared :as shared-b]
            [funcatron.tron.store.shared :as shared-s]
            [funcatron.tron.options :as f-opts]
            [compojure.route :refer [not-found resources]]
            )
  (:import (java.io File)
           (funcatron.abstractions MessageBroker MessageBroker$ReceivedMessage StableStore)
           (java.net URLEncoder)
           (java.util UUID)))


(set! *warn-on-reflection* true)

(defonce ^:private shutdown-server (atom nil))

(defonce ^:private -message-queue (atom nil))

(defonce ^:private -backing-store (atom nil))

(defonce ^:private the-network (atom {}))

(defonce ^:private route-map (atom []))

(defonce ^:private func-bundles (atom {}))

(defonce ^:private desired-state
         (atom {:front-ends 1
                :runners 1}))

(defonce ^:private actual-state
         (atom {:front-ends []
                :runners []}))

(def ^:private storage-directory
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

(defn- clean-network
  "Remove everything from the network that's old"
  []
  (let [too-old (- (System/currentTimeMillis) (* 1000 60 15))] ;; if we haven't seen a node in 15 minutes, clean it
    (swap!
      the-network
      (fn [cur]
        (into
          {}
          (remove #(> too-old (:last-seen (second %))) cur))))))




(defn- sha-and-swagger-for-file
  "Get the Sha256 and swagger information for the file"
  [file]
  (let [sha (fu/sha256 file)
        sha (fu/base64encode sha)

        {:keys [type, swagger] {:keys [host, basePath]} :swagger :as file-info}
        (fu/find-swagger-info file)]
    {:sha sha :type type :swagger swagger :host host :basePath basePath :file-info file-info}
    ))

(defn- load-func-bundles
  "Reads the func bundles from the storage directory"
  []
  (let [dir ^File @storage-directory
        files (filter #(and (.isFile ^File %)
                            (.endsWith (.getName ^File %) ".funcbundle"))
                      (.listFiles dir))]
    (doseq [file files]
      (let [{:keys [sha type swagger file-info]} (sha-and-swagger-for-file file)]
        (if (and type file-info)
          (swap! func-bundles assoc sha {:file file :swagger swagger}))))))

(defn- try-to-load-route-map
  "Try to load the route map"
  []
  (let [file (File. ^File @storage-directory "route_map.data")]
    (try
      (if (.exists file)
        (let [bundles @func-bundles
              data (slurp file)
              data (read-string data)]
          (if (map? data)
            (let [data (into {} (filter #(contains? bundles (-> % second :sha)) data))]
              (reset! route-map data)))))
      (catch Exception e (log/error e "Failed to load route map"))
      )))

(defn route-to-sha
  "Get a SHA for the route"
  [host path]
  (-> (str host ";" path)
      fu/sha256
      fu/base64encode
      URLEncoder/encode))

(defn add-to-route-map
  "Adds a route to the route table"
  [host path bundle-sha]
  (let [sha (route-to-sha host path)
        data {:host host :path path :queue sha :sha bundle-sha}]
    (swap!
      route-map
      (fn [rm]
        (let [rm (remove #(= (:queue %) sha) rm)
              rm (conj rm data)
              rm (sort
                   (fn [{:keys [path]} y]
                     (let [py (:path y)]
                       (- (count py) (count path)))
                     )
                   rm)]
          (into [] rm))
        )))
  )

(defn ^MessageBroker message-queue
  "The message queue/broker"
  []
  @-message-queue)

(defn- remove-other-instances-of-this-frontend
  "Finds other front-end instances with the same `instance-id` and tell them to die"
  [{:keys [from instance-id]}]
  (let [to-kill (filter (fn [[k v]]
                          (and (not= k from)
                               (= instance-id (:instance-id v))))
                        @the-network
                        )]
    (doseq [[k {:keys [instance-id]}] to-kill]
      (log/info (str "Killing old id " k " with instance-id " instance-id))
      (.sendMessage (message-queue) k
                    {:content-type "application/json"}
                    {:action "die"
                     :msg-id (.toString (UUID/randomUUID))
                     :instance-id instance-id
                     :at     (System/currentTimeMillis)
                     })
      )
    ))

(defn- send-route-map
  "Sends the route map to a destination"
  ([where] (send-route-map where @route-map))
  ([where the-map]
   (.sendMessage (message-queue) where
                 {:content-type "application/json"}
                 {:action "route"
                  :msg-id (.toString (UUID/randomUUID))
                  :routes the-map
                  :at     (System/currentTimeMillis)
                  })))

(defn- routes-changed
  "The routes changed, let all the front end instances know"
  [_ _ _ new-state]
  (fu/run-in-pool
    (fn []
      ;; write the file
      (let [file (File. ^File @storage-directory "route_map.data")]
        (try
          (spit file (pr-str new-state))
          (catch Exception e (log/error e "Failed to write route map"))
          ))

      (clean-network)
      (doseq [[k {:keys [type ]}] @the-network]
        (when (= type "frontend")
          (send-route-map k new-state))
        ))))

(defn ^StableStore backing-store
  "Returns the backing store object"
  []
  @-backing-store)

(defn- no-file-with-same-sha
  "Make sure there are no files in the storage-directory that have the same sha"
  [sha-to-test]
  (let [sha (URLEncoder/encode sha-to-test)
        files (filter #(.contains ^String % sha) (.list ^File @storage-directory))]
    (empty? files)))

(defn- upload-func-bundle
  "Get a func bundle"
  [{:keys [body] :as req}]
  (if body
    (let [file (File/createTempFile "func-a-" ".tron")]
      (cio/copy body file)
      (let [{:keys [sha type swagger host basePath file-info]} (sha-and-swagger-for-file file)]
        (if (and type file-info)
          (let [dest-file (File. ^File @storage-directory (str (System/currentTimeMillis)
                                                               "-"
                                                               (URLEncoder/encode sha) ".funcbundle"))]
            (when (no-file-with-same-sha sha)
              (cio/copy file dest-file))
            (.delete file)
            (swap! func-bundles assoc sha {:file dest-file :swagger swagger})
            {:status  200
             :body    {:accepted true
                       :type     type
                       :host     host
                       :route    basePath
                       :swagger  swagger
                       :sha256   sha}})
          {:status 400
           :body {:accepted false
                  :error "Could not determine the file type"}})))
    {:status 400
     :body {:accepted false
            :error "Must post a Func bundle file"}}
    )
  )

(defn- enable-func
  "Enable a Func bundle"
  [req]
  (println "Req " (get-in req [:headers "content-type"]) " body " (fu/kebab-keywordize-keys (:json-params req)))
  {:status 200
   :body {:success false
          :action "FIXME"}
   })

(defn- disable-func
  "Enable a Func bundle"
  [req]
  {:status 200
   :body {:success false
          :action "FIXME"}
   })

(defn- get-stats
  "Return statistics on activity"
  []
  {:status 200
   :body {:success false
          :action "FIXME"}
   })

(defroutes tron-routes
           "Routes for Tron"
           (POST "/api/v1/enable" req (enable-func req))
           (POST "/api/v1/disable" req (disable-func req))
           (GET "/api/v1/stats" [] get-stats)
           (POST "/api/v1/add_func"
                 req (upload-func-bundle req)))

(defn ring-handler
  "Handle http requests"
  [req]

  ((-> tron-routes
       wrap-json-response
       (rm-json/wrap-json-params)) req)
  )

(defmulti dispatch-tron-message
          "Dispatch the incoming message"
          (fn [msg & _] (-> msg :action))
          )

(defmethod dispatch-tron-message "heartbeat"
  [{:keys [from]} & _]
  (log/info (str "Heartbeat from " from))
  (clean-network)
  (swap! the-network assoc-in [from :last-seen] (System/currentTimeMillis))
  )

(defmethod dispatch-tron-message "awake"
  [msg & _]
  (log/info (str "awake from " msg))
  (clean-network)
  (swap! the-network assoc (:from msg) (merge msg {:last-seen (System/currentTimeMillis)}))
  (cond
    (= "frontend" (:type msg))
    (do
      (send-route-map (:from msg))
      (remove-other-instances-of-this-frontend msg)
      )
    )
  )

(defn- handle-tron-messages
  "Handle messages sent to the tron queue"
  [^MessageBroker$ReceivedMessage msg]
  (let [body (.body msg)
        body (fu/keywordize-keys body)]
    (try
      (dispatch-tron-message body msg)
      (catch Exception e (log/error e (str "Failed to dispatch message: " body))))))

(defn- connect-to-message-queue
  []
  (let [queue (shared-b/wire-up-queue)]
    (reset! -message-queue queue)
    (.listenToQueue queue (or "tron")
                    (fu/promote-to-function
                      (fn [msg]
                        (fu/run-in-pool (fn [] (handle-tron-messages msg))))))
    )
  )

(defn- connect-to-store
  "Connects to the backing store (e.g. ZookKeeper)"
  []
  (let [store (shared-s/wire-up-store)]
    (reset! -backing-store store)
    ))


(defn start-tron-server
  "Start the Tron mode server"
  []
  (require     ;; load a bunch of the namespaces to register wiring
    '[funcatron.tron.brokers.rabbitmq]
    '[funcatron.tron.brokers.inmemory]
    '[funcatron.tron.store.zookeeper]
    '[funcatron.tron.substrate.mesos-substrate])
  (load-func-bundles)                                       ;; load the bundles
  (try-to-load-route-map)
  (add-watch route-map ::nothing routes-changed)
  (fu/run-server #'ring-handler shutdown-server)
  (connect-to-message-queue)
  ;; (connect-to-store)
  )
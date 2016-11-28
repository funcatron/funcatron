(ns funcatron.tron.modes.tron-mode
  (:require [funcatron.tron.util :as fu]
            [clojure.java.io :as cio]
            [clojure.tools.logging :as log]
            [ring.middleware.json :as rm-json :refer [wrap-json-response]]
            [compojure.core :refer [GET defroutes POST]]
            [compojure.route :refer [not-found resources]]
            [funcatron.tron.modes.common :as common :refer [storage-directory]])
  (:import (java.io File)
           (funcatron.abstractions MessageBroker MessageBroker$ReceivedMessage)
           (java.net URLEncoder)
           (java.util UUID)))


(set! *warn-on-reflection* true)

(defonce ^:private shutdown-server (atom nil))

(defonce ^:private -message-queue (atom nil))

(defonce ^:private -backing-store (atom nil))

(defonce ^:private the-network (atom {}))

(defonce ^:private route-map (atom []))

(defonce ^:private func-bundles (atom {}))

#_(defonce ^:private desired-state
         (atom {:front-ends 1
                :runners 1}))

#_(defonce ^:private actual-state
         (atom {:front-ends []
                :runners []}))

(defn- clean-network
  "Remove everything from the network that's old"
  []
  (let [too-old (- (System/currentTimeMillis) (* 1000 60 3))] ;; if we haven't seen a node in 3 minutes, clean it
    (swap!
      the-network
      (fn [cur]
        (into
          {}
          (remove #(> too-old (:last-seen (second %))) cur))))))




(defn- try-to-load-route-map
  "Try to load the route map"
  []
  (let [file (fu/new-file @storage-directory "route_map.data")]
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

(defn- add-to-route-map
  "Adds a route to the route table"
  [host path bundle-sha]
  (let [sha (route-to-sha host path)
        data {:host host :path path :queue sha :sha bundle-sha}]
    ;; FIXME tell at least 1 runner to run the new code
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

(defn remove-from-route-map
  "Removes a func bundle from the route map"
  [_ _ bundle-sha]
  ;; FIXME tell runners to stop listening to queue
  (swap!
    route-map
    (fn [rm]
      (let [rm (remove #(= (:sha %) bundle-sha) rm)]
        (into [] rm))
      )))

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
                        )
        kill-keys (into #{} (map first to-kill))
        ]

    ;; no more messages to the instances we're removing
    (swap! the-network
           (fn [m]
             (into {} (remove #(kill-keys (first %)) m))))
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
      (let [file (fu/new-file @storage-directory "route_map.data")]
        (try
          (spit file (pr-str new-state))
          (catch Exception e (log/error e "Failed to write route map"))
          ))

      (clean-network)
      (doseq [[k {:keys [type ]}] @the-network]
        (when (= type "frontend")
          (send-route-map k new-state))
        ))))

#_(defn ^StableStore backing-store
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
  [{:keys [body]}]
  (if body
    (let [file (File/createTempFile "func-a-" ".tron")]
      (println "Body is " body)
      (cio/copy body file)
      (let [{:keys [sha type swagger host basePath file-info]} (common/sha-and-swagger-for-file file)]
        (if (and type file-info)
          (let [dest-file (fu/new-file @storage-directory (str (System/currentTimeMillis)
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
  [{:keys [json-params]}]
  (let [json-params (fu/keywordize-keys json-params)
        {:keys [sha256]} json-params]
    (println json-params)
    (if (not (and json-params sha256))
      ;; failed
      {:status 400
       :body   {:success false :error "Request must be JSON and include the 'sha256' of the Func bundle to enable"}}

      ;; find the Func bundle and enable it
      (let [{{:keys [host basePath]} :swagger :as bundle} (get @func-bundles sha256)]
        (if (not bundle)
          ;; couldn't find the bundle
          {:status 404
           :body   {:success false :error (str "Could not find Func bundle with SHA256: " sha256)}}

          (do
            (add-to-route-map host basePath sha256)
            {:status 200
             :body   {:success true :msg (str "Deployed Func bundle host: " host " basePath " basePath " sha256 " sha256)}})
          ))
      ))

  )

(defn- disable-func
  "Enable a Func bundle"
  [{:keys [json-params] {:keys [sha256]} :json-params}]
  (if (not (and json-params sha256))
    ;; failed
    {:status 400
     :body {:success false :error "Request must be JSON and include the 'sha256' of the Func bundle to disable"}}

    ;; find the Func bundle and enable it
    (let [{{:keys [host basePath]} :swagger :as bundle} (get @func-bundles sha256)]
      (if (not bundle)
        ;; couldn't find the bundle
        {:status 404
         :body   {:success false :error (str "Could not find Func bundle with SHA256: " sha256)}}

        (do
          (remove-from-route-map host basePath sha256)
          {:status 200
           :body   {:success true :msg (str "Disabled Func bundle host: " host " basePath " basePath " sha256 " sha256)}})
        ))
    ))

(defn- get-known-funcs
  "Return all the known func bundles"
  [& _]
  {:status 200
   :body {:func-bundles (map (fn [[k {{:keys [host basePath]} :swagger}]
                                  ]
                               {:sha256 k
                                :host host
                                :path basePath}) @func-bundles)}})

(defn- get-stats
  "Return statistics on activity"
  [& _]
  {:status 200
   :body {:success false
          :action "FIXME"}
   })

(defroutes tron-routes
           "Routes for Tron"
           (POST "/api/v1/enable" req (enable-func req))
           (POST "/api/v1/disable" req (disable-func req))
           (GET "/api/v1/stats" [] get-stats)
           (GET "/api/v1/known_funcs" [] get-known-funcs)
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

(defn- send-func-bundles
  "Send a list of the Func bundles to the Runner as well
  as the host and port for this instance"
  [_ ]
  ;; FIXME send func bundle
  )

(defn- enable-all-bundles
  "Tell the target to service all func bundles.
  This is a HACK that needs some FIXME logic to
  choose which runners run which bundles"
  [_]
  ;; FIXME tell the Func bundle to listen to all queue for all enabled bundles
  )

(defmethod dispatch-tron-message "awake"
  [{:keys [from type] :as msg} & _]
  (log/info (str "awake from " msg))
  (clean-network)
  (swap! the-network assoc from
         (merge
           msg
           {:last-seen (System/currentTimeMillis)}))
  (cond
    (= "frontend" type)
    (do
      (send-route-map from)
      (remove-other-instances-of-this-frontend msg)
      )

    (= "runner" type)
    (do
      (send-func-bundles from)
      (enable-all-bundles from))
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



#_(defn- connect-to-store
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
  (common/load-func-bundles @storage-directory func-bundles)                                       ;; load the bundles
  (try-to-load-route-map)
  (add-watch route-map ::nothing routes-changed)
  (fu/run-server #'ring-handler shutdown-server)
  (common/connect-to-message-queue -message-queue
                                   (or "tron")              ;; FIXME -- compute tron queue name
                                   #'handle-tron-messages)
  ;; (connect-to-store)
  )
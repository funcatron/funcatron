(ns funcatron.tron.modes.tron-mode
  (:require [funcatron.tron.util :as fu]
            [clojure.java.io :as cio]
            [clojure.tools.logging :as log]
            [ring.middleware.json :refer [wrap-json-response]]
            [compojure.core :refer [GET defroutes POST]]
            [funcatron.tron.brokers.shared :as shared-b]
            [funcatron.tron.store.shared :as shared-s]
            [compojure.route :refer [not-found resources]])
  (:import (java.io File)
           (funcatron.abstractions MessageBroker MessageBroker$ReceivedMessage StableStore)
           (java.net URLEncoder)))



(defonce ^:private shutdown-server (atom nil))

(defonce ^:private -message-queue (atom nil))

(defonce ^:private -backing-store (atom nil))

(defonce ^:private the-network (atom {}))

(defonce ^:private route-map (atom []))

(defonce ^:private desired-state
         (atom {:front-ends 1
                :runners 1}))

(defonce ^:private actual-state
         (atom {:front-ends []
                :runners []}))


(defn route-to-sha
  "Get a SHA for the route"
  [host path]
  (-> (str host ";" path)
      fu/sha256
      fu/base64encode
      URLEncoder/encode
      )
  )

(defn add-to-route-map
  "Adds a route to the route table"
  [host path]
  (let [sha (route-to-sha host path)
        data {:host host :path path :queue sha}]
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

(defn ^StableStore backing-store
  "Returns the backing store object"
  []
  @-backing-store)

(defn- upload-func-bundle
  "Get a func bundle"
  [{:keys [body] :as req}]
  (if body
    (let [file (File/createTempFile "func-a-" ".tron")]
      (cio/copy body file)
      (let [sha (fu/sha256 file)
            sha (fu/base64encode sha)
            {:keys [type, swagger] :as file-info} (fu/find-swagger-info file)]
        (if (and type file-info)
          {:status  200
           :headers {"Content-Type" "application/json"}
           :body    {:accepted true
                     :type     type
                     :host     (:host swagger)
                     :route    (:basePath swagger)
                     :swagger  swagger
                     :sha256   sha}}
          {:status 400
           :headers {"Content-Type" "application/json"}
           :body {:accepted false
                  :error "Could not determine the file type"}})))
    {:status 400
     :headers {"Content-Type" "application/json"}
     :body {:accepted false
            :error "Must post a Func bundle file"}}
    )
  )

(defn- enable-func
  "Enable a Func bundle"
  [req]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body {:success false
          :action "FIXME"}
   })

(defn- disable-func
  "Enable a Func bundle"
  [req]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body {:success false
          :action "FIXME"}
   })

(defn- get-stats
  "Return statistics on activity"
  []
  {:status 200
   :headers {"Content-Type" "application/json"}
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

  ((wrap-json-response tron-routes) req)
  )

(defmulti dispatch-tron-message
          "Dispatch the incoming message"
          (fn [msg & _] (-> msg :action))
          )

(defmethod dispatch-tron-message "heartbeat"
  [{:keys [from]} & _]
  (swap! the-network assoc-in [from :last-seen] (System/currentTimeMillis))
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
  (let [queue (shared-b/dispatch-wire-queue)]
    (reset! -message-queue queue)
    (.listenToQueue queue (or "tron") (fu/promote-to-function
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
  (fu/run-server #'ring-handler shutdown-server)
  (connect-to-message-queue)
  (connect-to-store))
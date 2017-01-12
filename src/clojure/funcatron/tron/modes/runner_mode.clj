(ns funcatron.tron.modes.runner-mode
  (:require [funcatron.tron.util :as fu]
            [funcatron.tron.modes.common :as common]
            [funcatron.tron.brokers.shared :as shared-b]
            [org.httpkit.client :as http]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [funcatron.tron.options :as opts]
            [funcatron.tron.routers.jar-router :as jarjar]
            [clojure.java.io :as cio])
  (:import (funcatron.abstractions MessageBroker$ReceivedMessage MessageBroker Lifecycle Router)
           (java.net URLEncoder)
           (java.io File)))

(set! *warn-on-reflection* true)

(defn- ^MessageBroker queue-from-state
  "Get the message queue from the state"
  [state]
  (-> state ::message-queue))

(defn- handle-http-request
  "Take an HTTP request from a frontend, run it through
  the Java function, and package the response..."
  [{:keys [::stats]} sha ^Router router ^MessageBroker$ReceivedMessage msg]

  (let [router-msg (.brokerMessageToRouterMessage router msg)
        res
        (fu/time-execution
          (.routeMessage router router-msg))
        res (dissoc res :value :exception)
        res (fu/square-numbers res)
        res (assoc res :cnt 1)
        ]

    (swap! stats (fn [m]
                   (->
                     m
                     (update sha (fn [x]
                                   (merge-with + x res)))
                     (update {:sha sha
                              :uri (.uri router-msg)
                              :method (.method router-msg)}
                             (fn [x] merge-with + x res)))))))

(defn- load-sha-and-then
  "If the SHA of the func bundle is not known, get it from the Tron and then execute the function"
  [sha {:keys [::func-bundles ::keep-running ::tron-host ::opts] :as state} the-func]

  (let [do-run (fn [the-file] (when @keep-running (the-func the-file)))]
    (if-let [{:keys [file]} (get @func-bundles sha)]
      ;; okay, we've got the sha already, so just do what we've gotta do
      (do
        (do-run file))

      ;; whoops... we've gotta ask the Tron for the func bundle
      (do
        (http/get

          ;; get the bundle
          (str "http://" (:host @tron-host)
               ":" (:port @tron-host)
               "/api/v1/bundle/"
               (URLEncoder/encode sha))
          ;; as a stream
          {:as :stream}

          ;; the async call-back
          (fn [{:keys [status body error]}]
            (if (or error (not (= 200 status)))
              ;; something went wrong...
              (timbre/error (str "Failed to load func bundle with sha " sha " error " error " status " status))

              ;; save the bundle, update the table, and then execute the functions
              (try
                (let [dir (common/calc-storage-directory opts)
                      file (File. dir (str (System/currentTimeMillis)
                                           "-"
                                           (URLEncoder/encode sha) ".funcbundle"))]
                  (cio/copy body file)
                  (when-let [{:keys [sha] :as info} (common/sha-for-file file)]
                    (swap! func-bundles assoc sha info)
                    (do-run file)))
                (catch Exception e
                  (timbre/error e (str "Failed to load func bundle with sha " sha)))))))))))

(defn- send-ping
  "Sends a ping to the Tron"
  [{:keys [::stats] :as state}]



  (let [cur-stats @stats]
    (reset! stats {})

    (.sendMessage
      (queue-from-state state)
      (common/tron-queue)
      {:content-type "application/json"}
      {:action "heartbeat"
       :version (:version fu/version-info)
       :msg-id (fu/random-uuid)
       :from   (::uuid state)
       :type   "runner"
       :stats  cur-stats
       :routes (map (fn [[_ v]] (dissoc v :end-func :router)) (-> state ::routes deref))
       :at     (System/currentTimeMillis)
       })))

(defmulti dispatch-runner-message
          "Dispatch the incoming message"
          (fn [msg & _] (-> msg :action))
          )


(defmethod dispatch-runner-message "all-bundles"
  [{:keys [bundles tron-host]} _ {:keys [::func-bundles] :as state}]
  (info (str "'All Bundles' message "))
  (reset! (::tron-host state) tron-host)
  (let [my-bundles @func-bundles

        ;; get a list of all bundles we don't know about
        unknown (remove
                  #(contains? my-bundles (:sha %))
                  bundles)]
    (when (not (empty? unknown))
      (fu/run-in-pool
        (fn []
          (doseq [{:keys [:sha]} unknown]
            (load-sha-and-then
              sha
              state
              (fn [& _])))))))
  )

(defmethod dispatch-runner-message "die"
  [_ _ {:keys [::this]}]
  (info (str "Handling 'die'  "))
  (.endLife ^Lifecycle this))

(defmethod dispatch-runner-message "tron-info"
  [{:keys [tron-host]} _ state]
  (reset! (::tron-host state) tron-host)
  )


(defn- stop-listening-to
  "Stops listening to a particular queue"
  [state queue-name {:keys [end-func ^Router router]}]

  (.run ^Runnable end-func)
  (.endLife router)
  (swap! (-> state ::routes) dissoc queue-name)
  )

(defmethod dispatch-runner-message "enable"
  [{:keys [sha props host basePath]} _ {:keys [::message-queue ::routes] :as state}]


  (load-sha-and-then
    sha
    state
    (fn [file]
      (try
        (let [router (jarjar/build-router file props true)
              queue (fu/route-to-sha host basePath)
              _ (when-let [info (get @routes queue)]
                  (stop-listening-to state queue info))
              end-func
              (shared-b/listen-to-queue
                message-queue queue
                (fn [msg]
                  (fu/run-in-pool
                    (fn [] (try (handle-http-request
                                  state
                                  queue
                                  router
                                  msg)
                                (catch Exception e (error e (str "Failed to service request on queue " queue)))
                                )))))]
          (info (str "Successfully deployed SHA " sha " for queue " queue))
          (swap! routes assoc queue
                 {:end-func end-func
                  :queue    queue
                  :host     host
                  :router   router
                  :path     basePath
                  :sha      sha})
          (fu/run-in-pool (fn [] (send-ping state)))
          )
        (catch Exception e
          (error e (str "Failed to start bundle with sha " sha)))
        ))))

(defmethod dispatch-runner-message "disable"
  [{:keys [host basePath]} _ {:keys [::message-queue ::routes] :as state}]

  (let [queue (fu/route-to-sha host basePath)]
    (when-let [info (get @routes queue)]
      (stop-listening-to state queue info)))

  (fu/run-in-pool (fn [] (send-ping state))))

(defn- handle-runner-message
  "Handle messages sent to the tron queue"
  [^MessageBroker$ReceivedMessage msg state]
  (let [body (.body msg)
        body (fu/keywordize-keys body)]
    (try
      (dispatch-runner-message body msg state)
      (catch Exception e (error e (str "Failed to dispatch message: " body))))))



(defn- schedule-ping
  "Ping the tron and then schedule for another ping"
  [state]
  (let [keep-running (-> state ::keep-running deref)]
    (trace (str "In scheduled ping... keep running: " keep-running))
    (when keep-running

      (try
        (send-ping state)
        (catch Exception e
          (error e "Failed to ping the Tron")))
      (fu/run-after (fn [] (schedule-ping state)) 10000)
      )))

(defn- wake-up
  "Tell the Tron we're awake and ready for routes and such"
  [state]
  (info (str "Waking running " (::uuid state)))
  (.sendMessage
    (queue-from-state state)
    (common/tron-queue)
    {:content-type "application/json"}
    {:action "awake"
     :type   "runner"
     :version (:version fu/version-info)
     :msg-id (fu/random-uuid)
     :from   (-> state ::uuid)
     :at     (System/currentTimeMillis)
     })
  (schedule-ping state)
  )

(defmethod dispatch-runner-message "resend-awake"
  [_ _ state]
  (wake-up state)
  )


(defn- i-died
  "Tell the Tron we've died"
  [state]
  (.sendMessage
    (queue-from-state state)
    (common/tron-queue)
    {:content-type "application/json"}
    {:action "died"
     :version (:version fu/version-info)
     :msg-id (fu/random-uuid)
     :from   (-> state ::uuid)
     :at     (System/currentTimeMillis)
     }))


(defn ^Lifecycle build-runner
  "Start the Tron mode server"
  (
   [^MessageBroker queue opts]

   (let [
         func-bundles (atom {})
         keep-running (atom true)
         routes (atom {})
         tron-host (atom nil)
         this (atom nil)
         stats (atom {})
         my-uuid (str "RU-" (fu/random-uuid))

         state {
                ::message-queue queue
                ::func-bundles  func-bundles
                ::keep-running  keep-running
                ::routes        routes
                ::this          this
                ::stats         stats
                ::tron-host     tron-host
                ::uuid          my-uuid}
         ]


     (reset! func-bundles (common/load-func-bundles (common/calc-storage-directory opts))) ;; load the bundles
     (let [ret
           (reify Lifecycle
             (startLife [_]
               (shared-b/listen-to-queue
                 queue my-uuid
                 (fn [msg]
                   (fu/run-in-pool (fn [] (handle-runner-message msg state)))))
               (wake-up state))

             (endLife [_]
               (reset! keep-running false)
               (i-died state)
               (shared-b/close-all-listeners queue)
               (.close queue)
               )

             (allInfo [_] {::message-queue queue
                           ::func-bundles  @func-bundles
                           ::routes        @routes
                           ::tron-host     @tron-host
                           ::this          @this
                           ::stats @stats
                           ::uuid          my-uuid})
             )]
       (reset! this ret)
       ret
       ))))

(defn ^Lifecycle build-runner-from-opts
  "Builds the runner from options... and if none are passed in, use global options"
  ([] (build-runner-from-opts @opts/command-line-options))
  ([opts]
   (require                                                 ;; load a bunch of the namespaces to register wiring
     '[funcatron.tron.brokers.rabbitmq]
     '[funcatron.tron.brokers.inmemory]
     '[funcatron.tron.store.zookeeper]
     '[funcatron.tron.substrate.mesos-substrate])
   (let [queue (shared-b/wire-up-queue opts)]
     (build-runner queue opts))))
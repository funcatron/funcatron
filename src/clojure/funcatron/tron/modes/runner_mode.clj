(ns funcatron.tron.modes.runner-mode
  (:require [funcatron.tron.util :as fu]
            [funcatron.tron.modes.common :as common]
            [funcatron.tron.brokers.shared :as shared-b]
            [org.httpkit.client :as http]
            [taoensso.timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [funcatron.tron.options :as opts]
            [clojure.java.io :as cio])
  (:import (funcatron.abstractions MessageBroker$ReceivedMessage MessageBroker Lifecycle)
           (java.util Date)
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
  [state sha ^MessageBroker$ReceivedMessage msg]

  (let [msg-body (-> msg .body fu/kebab-keywordize-keys)]

    ;; FIXME yeah... this is a hack... we need to run the request like in dev_mode
    (let [queue (queue-from-state state)]
      (.sendMessage
        queue
        (:reply-queue msg-body)
        {:content-type "application/json"}
        {:action     "answer"
         :msg-id     (fu/random-uuid)
         :request-id (:reply-to msg-body)
         :answer     {:headers
                              [["Content-Type" "text/html"]]
                      :status 200
                      :body
                              (fu/base64encode (str "<html><head><title>" sha " </title></head>
                <body>
                Hello World... sha " sha " at " (Date.)
                                                    "

                  </body>
                  </html>"))}
         :at         (System/currentTimeMillis)
         }

        )))
  )

(defn- load-sha-and-then
  "If the SHA of the func bundle is not known, get it from the Tron and then execute the function"
  [sha {:keys [::func-bundles ::keep-running ::tron-host ::opts] :as state} the-func]
  (let [do-run (fn [] (when @keep-running (the-func)))]
    (if (contains? @func-bundles sha)
      ;; okay, we've got the sha already, so just do what we've gotta do
      (do-run)

      ;; whoops... we've gotta ask the Tron for the func bundle
      (http/get

        ;; get the bundle
        (str "http://" (:host tron-host)
                     ":" (:port tron-host)
                     "/api/v1/bundle/"
                     (URLEncoder/encode sha))
        ;; as a stream
        {:as :stream}

        ;; the async call-back
        (fn [{:keys [status body error]}]
          (if (or error (not (= 200 status)))
            ;; something went wrong...
            (error (str "Failed to load func bundle with sha " sha "error " error " status " status))

            ;; save the bundle, update the table, and then execute the functions
            (try
              (let [dir (common/calc-storage-directory opts)
                    file (File. dir (str (System/currentTimeMillis)
                                         "-"
                                         (URLEncoder/encode sha) ".funcbundle"))]
                (cio/copy body file)
                (when-let [[sha info] (common/get-bundle-info file)]
                  (swap! func-bundles assoc sha info)
                  (do-run)))
              (catch Exception e (error e (str "Failed to load func bundle with sha " sha))))))))))

(defn- send-ping
  "Sends a ping to the Tron"
  [state]

  (.sendMessage
    (queue-from-state state)
    (common/tron-queue)
    {:content-type "application/json"}
    {:action "heartbeat"
     :msg-id (fu/random-uuid)
     :from   (::uuid state)
     :type   "runner"
     :routes (map (fn [[_ v]] (dissoc v :end-func)) (-> state ::routes deref))
     :at     (System/currentTimeMillis)
     }))

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
              identity))))))
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
  [state queue-name {:keys [end-func]}]
  (.run ^Runnable end-func)
  (swap! (-> state ::routes) dissoc queue-name)
  )

(defmethod dispatch-runner-message "associate"
  [{:keys [sha queue host path]} _ {:keys [::message-queue ::routes] :as state}]
  (when-let [info (get @routes queue)]
    (stop-listening-to state queue info))

  (load-sha-and-then
    sha
    state
    (fn []
      (let [end-func
            (.listenToQueue
              ^MessageBroker message-queue queue
              (fu/promote-to-function
                (fn [msg]
                  (fu/run-in-pool
                    (fn [] (handle-http-request
                             state
                             sha
                             msg))))))]
        (swap! routes assoc queue
               {:end-func   end-func
                :queue queue
                :host       host
                :path   path
                :sha     sha})
        (fu/run-in-pool (fn [] (send-ping state)))
        ))))


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
  (.sendMessage
    (queue-from-state state)
    (common/tron-queue)
    {:content-type "application/json"}
    {:action "awake"
     :type   "runner"
     :msg-id (fu/random-uuid)
     :from   (-> state ::uuid)
     :at     (System/currentTimeMillis)
     })
  (schedule-ping state)
  )

(defn- i-died
  "Tell the Tron we've died"
  [state]
  (.sendMessage
    (queue-from-state state)
    (common/tron-queue)
    {:content-type "application/json"}
    {:action "died"
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

         my-uuid (str "RU-" (fu/random-uuid))

         state {
                ::message-queue queue
                ::func-bundles  func-bundles
                ::keep-running  keep-running
                ::routes        routes
                ::this          this
                ::tron-host tron-host
                ::uuid          my-uuid}
         ]
     (.listenToQueue
       queue
       my-uuid
       (fu/promote-to-function
         (fn [msg]
           (fu/run-in-pool (fn [] (handle-runner-message msg state))))))

     (reset! func-bundles (common/load-func-bundles (common/calc-storage-directory opts))) ;; load the bundles
     (let [ret
           (reify Lifecycle
             (startLife [_]
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
                           ::tron-host @tron-host
                           ::this          @this
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
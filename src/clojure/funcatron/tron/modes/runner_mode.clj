(ns funcatron.tron.modes.runner-mode
  (:require [funcatron.tron.util :as fu]
            [funcatron.tron.modes.common :as common]
            [funcatron.tron.brokers.shared :as shared-b]
            [taoensso.timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [funcatron.tron.options :as opts])
  (:import (funcatron.abstractions MessageBroker$ReceivedMessage MessageBroker Lifecycle)
           (java.util Date)))

(set! *warn-on-reflection* true)

(defn- ^MessageBroker queue-from-state
  "Get the message queue from the state"
  [state]
  (-> state ::message-queue))

(defn- handle-http-request
  "Take an HTTP request from a frontend, run it through
  the Java function, and package the response..."
  [state sha256 msg]
  ;; FIXME yeah... this is a hack... we need to run the request like in dev_mode
  (let [queue (queue-from-state state)]
    (.sendMessage
      queue
      (:reply-queue msg)
      {:content-type "application/json"}
      {:action     "answer"
       :msg-id     (fu/random-uuid)
       :request-id (:reply-to msg)
       :answer     {:headers
                            [["Content-Type" "text/html"]]
                    :status 200
                    :body
                            (fu/base64encode (str "<html><head><title>" sha256 " </title></head>
                <body>
                Hello World... sha " sha256 " at " (Date.)
                                                  "

                </body>
                </html>"))}
       :at         (System/currentTimeMillis)
       }

      ))
  )

(defn- send-ping
  "Sends a ping to the Tron"
  [state]
  (.sendMessage (queue-from-state state) (or "tron")        ;; FIXME -- tron queue name
                {:content-type "application/json"}
                {:action "heartbeat"
                 :msg-id (fu/random-uuid)
                 :from   (::uuid state)
                 :type   "runner"
                 :routes []                                 ;; FIXME -- what routes are we handling
                 :at     (System/currentTimeMillis)
                 }))

(defmulti dispatch-runner-message
          "Dispatch the incoming message"
          (fn [msg & _] (-> msg :action))
          )


(defmethod dispatch-runner-message "die"
  [{:keys [from]} _ state]
  (info (str "'die' from " from))
  (reset! (::keep-running state) false))

(defmethod dispatch-runner-message "heartbeat"
  [{:keys [from]} _ _]
  (info (str "'heartbeat' from " from))
  )

(defmethod dispatch-runner-message "service"
  [{:keys [from]} _ _]                                      ;; FIXME
  (info (str "'heartbeat' from " from))
  )

(defn- stop-listening-to
  "Stops listening to a particular queue"
  [state queue-name {:keys [end-func]}]
  (.run ^Runnable end-func)
  (swap! (-> state ::routes) dissoc queue-name)
  )

(defmethod dispatch-runner-message "associate"
  [{:keys [sha256 queue-name]} _ state]
  (let [{:keys [::message-queue ::routes]} state]
    (when-let [info (get @routes queue-name)]
      (stop-listening-to state queue-name info))


    (let [end-func
          (.listenToQueue ^MessageBroker message-queue queue-name
                          (fu/promote-to-function
                            (fn [msg]
                              (fu/run-in-pool (fn [] (handle-http-request
                                                       state
                                                       sha256
                                                       msg))))))]
      (swap! routes assoc queue-name
             {:end-func end-func
              :sha256   sha256})
      (fu/run-in-pool (fn [] (send-ping state)))
      )
    ))


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
    (info (str "In scheduled ping... keep running: " keep-running))
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
    (or "tron")                                             ;; FIXME -- tron queue name

    {:content-type "application/json"}
    {:action "awake"
     :type   "runner"
     :msg-id (fu/random-uuid)
     :from   (-> state ::uuid)
     :at     (System/currentTimeMillis)
     })
  (schedule-ping state)
  )


(defn ^Lifecycle build-runner
  "Start the Tron mode server"
  (
   [^MessageBroker queue opts]

   (let [
         func-bundles (atom {})
         keep-running (atom true)
         routes (atom {})

         my-uuid (str "RU-" (fu/random-uuid))

         state {
                ::message-queue queue
                ::func-bundles  func-bundles
                ::keep-running  keep-running
                ::routes        routes
                ::uuid          my-uuid}

         end-func (.listenToQueue
                    queue
                    my-uuid
                    (fu/promote-to-function
                      (fn [msg]
                        (fu/run-in-pool (fn [] (handle-runner-message msg state))))))

         ]
     (reset! func-bundles (common/load-func-bundles (common/calc-storage-directory opts))) ;; load the bundles
     (reify Lifecycle
       (startLife [_]
         (wake-up state))

       (endLife [_]
         (end-func)
         (shared-b/close-all-listeners queue)
         (.close queue)
         (reset! keep-running false))

       (allInfo [_] {::message-queue queue
                     ::func-bundles  @func-bundles
                     ::routes        @routes
                     ::uuid          my-uuid})
       ))))

(defn ^Lifecycle build-runner-from-opts
  "Builds the runner from options... and if none are passed in, use global options"
  ([] (build-runner-from-opts @opts/command-line-options))
  ([opts]
   (require     ;; load a bunch of the namespaces to register wiring
     '[funcatron.tron.brokers.rabbitmq]
     '[funcatron.tron.brokers.inmemory]
     '[funcatron.tron.store.zookeeper]
     '[funcatron.tron.substrate.mesos-substrate])
   (let [queue (shared-b/wire-up-queue opts)]
     (build-runner queue opts))))
(ns funcatron.tron.modes.runner-mode
  (:require [funcatron.tron.util :as fu]
            [funcatron.tron.modes.common :as common :refer [storage-directory]]
            [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            )
  (:import (funcatron.abstractions MessageBroker$ReceivedMessage MessageBroker Lifecycle)
           (java.util UUID Date)))

(set! *warn-on-reflection* true)

(defn- ^MessageBroker queue-from-state
  "Get the message queue from the state"
  [state]
  (-> state :message-queue deref))

(defn- handle-http-request
  "Take an HTTP request from a frontend, run it through
  the Java function, and package the response..."
  [state sha256 msg]
  (let [queue (queue-from-state state)]
    (.sendMessage
      queue
      (:reply-queue msg)
      {:content-type "application/json"}
      {:action     "answer"
       :msg-id     (.toString (UUID/randomUUID))
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
                 :msg-id (.toString (UUID/randomUUID))
                 :from   (:my-uuid state)
                 :type   "runner"
                 :routes []                                 ;; FIXME -- what routes are we handling
                 :at     (System/currentTimeMillis)
                 }))

(defmulti dispatch-runner-message
          "Dispatch the incoming message"
          (fn [msg & _] (-> msg :action))
          )


(defmethod dispatch-runner-message "die"
  [{:keys [from]} & params]
  (log/info (str "'die' from " from))
  (reset! (-> params last :keep-running) false))

(defmethod dispatch-runner-message "heartbeat"
  [{:keys [from]} & _]
  (log/info (str "'heartbeat' from " from))
  )

(defmethod dispatch-runner-message "service"
  [{:keys [from]} & _]
  (log/info (str "'heartbeat' from " from))
  )

(defn- stop-listening-to
  "Stops listening to a particular queue"
  [state queue-name {:keys [end-func] :as info}]
  (.run ^Runnable end-func)
  (swap! (-> state :routes) dissoc queue-name)
  )

(defmethod dispatch-runner-message "associate"
  [{:keys [sha256 queue-name]} & params]
  (let [{:keys [^MessageBroker message-queue routes] :as state} (last params)]
    (when-let [info (get @routes queue-name)]
      (stop-listening-to state queue-name info))


    (let [end-func
          (.listenToQueue message-queue queue-name
                          (fu/promote-to-function
                            (fn [msg]
                              (fu/run-in-pool (fn [] (handle-http-request
                                                       state
                                                       sha256
                                                       msg))))))]
      (swap! routes assoc queue-name {:end-func end-func
                                      :sha256   sha256
                                      })
      (fu/run-in-pool (fn [] (send-ping state)))
      )
    ))


(defn- handle-runner-message
  "Handle messages sent to the tron queue"
  [^MessageBroker$ReceivedMessage msg]
  (let [body (.body msg)
        body (fu/keywordize-keys body)]
    (try
      (dispatch-runner-message body msg)
      (catch Exception e (log/error e (str "Failed to dispatch message: " body))))))



(defn- schedule-ping
  "Ping the tron and then schedule for another ping"
  [state]
  (let [keep-running (-> state :keep-running deref)]
    (log/info (str "In scheduled ping... keep running: " keep-running))
    (when keep-running

      (try
        (send-ping state)
        (catch Exception e
          (log/error e "Failed to ping the Tron")))
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
     :msg-id (.toString (UUID/randomUUID))
     :from   (-> state :my-uuid)
     :at     (System/currentTimeMillis)
     })
  (schedule-ping state)
  )


(defn ^Lifecycle start-runner
  "Start the Tron mode server"
  (
   [opts]
   (require                                                 ;; load a bunch of the namespaces to register wiring
     '[funcatron.tron.brokers.rabbitmq]
     '[funcatron.tron.brokers.inmemory]
     )
   (let [
         func-bundles (atom {})
         keep-running (atom true)
         routes (atom {})

         my-uuid (str "RU-" (.toString (UUID/randomUUID)))

         state {
                :func-bundles  func-bundles
                :keep-running  keep-running
                :routes        routes
                :my-uuid       my-uuid}
         dispatch-func (fn [& x]
                         (let [params (into [] x)
                               params (conj params state)]
                           (apply #'handle-runner-message params)))

         queue-info (common/connect-to-message-queue opts my-uuid dispatch-func)
         ]
     (common/load-func-bundles @storage-directory func-bundles) ;; load the bundles
     (reify Lifecycle
       (startLife [this])
       (endLife [_]
         (-> queue-info :common:end-func (apply []))
         (.close ^MessageBroker (queue-info :common:queue))
         (reset! keep-running false)
         )
       (allInfo [_] {::message-queue queue-info
                     ::func-bundles  @func-bundles
                     ::routes        @routes
                     ::uuid          my-uuid})
       )


     (wake-up state)
     state)))
(ns funcatron.tron.substrate.mesos-substrate
  "Create a substrate for Mesos clusters"
  (:require [funcatron.tron.options :as f-opts]
            [clojure.tools.logging :as log]
            [dragonmark.util.props :as dp]
            [funcatron.tron.substrate.shared :as shared]
            [funcatron.tron.util :as fu])
  (:import (funcatron.abstractions ContainerSubstrate ContainerSubstrate$TaskState ContainerSubstrate$ServiceType)
           (funcatron.mesos MesosController
                            MesosController$StateManager
                            MesosController$DesiredTask
                            MesosController$EndOfSession)
           (java.net URI)
           (funcatron.helpers Tuple2 Tuple3)
           (java.util UUID List)
           (org.apache.mesos.v1 Protos$FrameworkID)
           (java.util.concurrent.atomic AtomicBoolean)))


(defn ^ContainerSubstrate create-substrate
  "Creates a ContainerSubstrate instance connected to the Mesos cluster"
  ([] (create-substrate (merge @dp/info (:options @f-opts/command-line-options))))

  ([opts]
   (let [;; compute the Mesos URI
         mesos-uri (URI. (str
                           (or
                             (:mesos_uri opts)
                             "http://m1.dcos:5050")
                           "/api/v1/scheduler"))

         ;; we need a stable FrameworkID
         fid (->
               (Protos$FrameworkID/newBuilder)
               (.setValue (str "funcatron-" (-> (UUID/randomUUID) .toString)))
               .build
               )

         ;; a state variable indicating if we should end the connection to Mesos
         end-it? (AtomicBoolean. false)

         ;; the tasks we want to launch
         desired-tasks (atom [])

         ;; the current status/state for all the tasks
         current-state (atom {})

         ;; a list of tasks to shut down
         to-shutdown (atom [])

         ;; the event stream (JavaRX) from Mesos
         event-stream (atom nil)

         ;; all the monitor functions
         monitors (atom {})

         ;; a function that removes stopped tasks and cleans up the monitor functions
         clean-state                                        ;; remove stopped tasks more than 5 minutes old
         (fn []
           (let [five-min-ago (- (System/currentTimeMillis) (* 5 1000 60))]
             ;; remove all functions that are stopped and have been stopped for 5+ minutes
             (swap!
               current-state
               (fn [st]
                 (into
                   {}
                   (remove
                     (fn [[_ v]]
                       (and (= ContainerSubstrate$TaskState/Stopped (:state v))
                            (< (:last-time v) five-min-ago))
                       )
                     st))))

             ;; now remove all monitors for UUIDs not in current state
             (let [cs @current-state]
               (swap! monitors
                      (fn [mon]
                        (into {} (filter (fn [[k _]] (contains? cs k)) mon)))))
             ))

         ;; build the connection to Mesos
         state-mgr
         (reify MesosController$StateManager

           ;; we have an update
           (update [this task-id status state]
             ;; update things asynchronously
             (fu/run-in-pool
               (fn []
                 ;; remove the task from any of the add and remove lists
                 (swap! desired-tasks #(into [] (remove (fn [{:keys [uuid]}] (= uuid task-id)) %)))
                 (swap! to-shutdown #(into [] (remove (fn [{:keys [uuid]}] (= uuid task-id)) %)))

                 (clean-state)

                 ;; update the state no matter what
                 (swap! current-state assoc-in [task-id :state] state)

                 ;; if we have a formal status, then update the status and
                 ;; send a message to the monitors
                 (when status
                   (let [status (-> status MesosController/statusToMap fu/kebab-keywordize-keys)
                         info {:id          task-id
                               :uuid        task-id
                               :latest      status
                               :last-time   (System/currentTimeMillis)
                               :agent-id    (:agent-id status)
                               :executor-id (:executor-id status)}]
                     ;; update the status in the atom
                     (let [whole-status (swap! current-state update task-id merge info)
                           whole-status (fu/stringify-keys whole-status)]

                       ;; for all the monitors, tell them about the status
                       ;; but do it in thread pools do we don't block the main update
                       (doseq [func (get @monitors task-id)]
                         (fu/run-in-pool
                           (fn []
                             (try
                               (let [cfunc (fu/to-clj-func func)]
                                 (cfunc (Tuple3. task-id status whole-status)))
                               (catch Exception e
                                 (log/error e (str "Failed to update status for " task-id)))))))))))))

           ;; is the session over?
           (isDone [this] (.get end-it?))

           ;; get the framework id
           (getFwId [this] fid)

           ;; return a list of desired tasks
           (currentDesiredTasks [this]
             (clean-state)
             (mapv
               (fn [{:keys [^UUID id ^ContainerSubstrate$ServiceType type]}]
                 (MesosController$DesiredTask. id
                                               (.-dockerImage type)
                                               (.-memory type)
                                               (.-cpu type)
                                               "*"
                                               (.-env type)))
               @desired-tasks))

           ;; get a list of tasks to shut down
           (shutdownTasks [this]
             (mapv
               (fn [{:keys [agent-id executor-id uuid]}]
                 (Tuple3. agent-id executor-id uuid))
               @to-shutdown))
           )

         ;; the client connection to Mesos
         client (MesosController/buildClient mesos-uri state-mgr)]

     ;; the Mesos controller runs in a JavaRX stream which is infinite as
     ;; long as the connection is there... so we need a thread to run
     ;; the process of dealing with the events
     (.start
       (Thread.
         ^Runnable
         (fn []
           (let [stream (.openStream client)]
             (log/info "In Mesos Substrate Runner")
             (reset! event-stream stream)
             (try
               (let [res (.await stream)]
                 (log/info (str "Finished Mesos Substrate Runner: " res)))
               (catch MesosController$EndOfSession eos
                 (log/info "Finished Mesos Substrate Runner Normally"))
               (catch Exception e
                 (log/error e "Finished Mesos Substrate Runner with Error"))
               )
             ))
         "Mesos Substrate Runner"))

     ;; finally, we return a "ContainerSubstrate" instance
     (reify ContainerSubstrate

       ;; return debugging information
       (allInfo [this] {:desired       @desired-tasks
                        :current-state @current-state
                        :shutdown      @to-shutdown
                        :monitors      @monitors
                        :event-stream  @event-stream
                        :client        client})

       ;; start a service where type is the docker container name
       (startService [_ type monitor]
         (let [id (UUID/randomUUID)]
           (when monitor
             (swap! monitors update id (fn [v] (conj v monitor))))
           (swap! desired-tasks conj {:id id :type type})
           id))

       ;; stope the service
       (stopService [this id monitor]
         (let [{:keys [state agent-id executor-id] :as current} (get id @current-state)]
           (if (and current
                    (not
                      (#{ContainerSubstrate$TaskState/Stopping
                         ContainerSubstrate$TaskState/RequestedStop
                         ContainerSubstrate$TaskState/Stopped} state)))
             (do
               (when monitor
                 (swap! monitors update id (fn [v] (conj v monitor))))
               (swap! to-shutdown conj {:agent-id    agent-id
                                        :executor-id executor-id
                                        :uuid        id})
               (swap! current-state assoc-in [id :state] ContainerSubstrate$TaskState/RequestedStop)
               ))
           )
         )
       (disconnect [this] (.set end-it? true))
       (status [this id]
         (let [{:keys [state info] :as current} (get @current-state id)]
           (if current
             (Tuple2. state (fu/camel-stringify-keys info))))))))
  )

(defmethod shared/dispatch-substrate "mesos"
  [opts]
  (create-substrate opts))

(defmethod shared/dispatch-substrate nil
  [opts]
  (create-substrate opts))
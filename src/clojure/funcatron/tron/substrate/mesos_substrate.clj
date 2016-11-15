(ns funcatron.tron.substrate.mesos-substrate
  (:require [funcatron.tron.options :as f-opts]
            [dragonmark.util.props :as dp]
            [funcatron.tron.util :as fu])
  (:import (funcatron.abstractions ContainerSubstrate ContainerSubstrate$TaskState)
           (funcatron.mesos MesosController MesosController$StateManager MesosController$DesiredTask)
           (java.net URI)
           (funcatron.helpers Tuple2 Tuple3)
           (java.util UUID Map List)
           (org.apache.mesos.v1 Protos$FrameworkID)))

(defn create-substrate
  "Creates a ContainerSubstrate instance connected to the Mesos cluster"
  ([] (create-substrate (merge @dp/info (:options @f-opts/command-line-options))))

  ([opts]
   (let [mesos-uri (URI. (str (or (:mesos_uri opts)
                                  "http://m1.dcos:5050")
                              "/api/v1/scheduler"
                              ))
         fid (->
                 (Protos$FrameworkID/newBuilder)
                 (.setValue (str "funcatron-" (-> (UUID/randomUUID) .toString)))
                 .build
                 )
         _ (println "Mesos URI " mesos-uri)
         desired-tasks (atom [])
         current-state (atom {})
         to-shutdown (atom [])
         state-mgr
         (reify MesosController$StateManager
           (update [this task-id status state]
             (println "In update for " task-id)
             ;; remove the task from any of the add and remove lists
             (swap! desired-tasks #(into [] (remove (fn [{:keys [uuid]}] (= uuid task-id)) %)))
             (swap! to-shutdown #(into [] (remove (fn [{:keys [uuid]}] (= uuid task-id)) %)))
             (swap! current-state assoc-in [task-id :state] state)
             (when status
               (let [status (-> status MesosController/statusToMap fu/kebab-keywordize-keys)
                     info {:id task-id
                           :uuid task-id
                           :latest status
                           :agent-id (:agent-id status)
                           :executor-id (:executor-id status)}]
                 (println "Updating status for " task-id " to " status)
                 (swap! current-state update task-id merge info)
                 ))
             )
           (isDone [this] )
           (getFwId [this] fid)
           (currentDesiredTasks [this]
             (println "Asking for desired tasks")
             (mapv
               (fn [{:keys [^UUID id ^String type ^List env-vars]}]
                 ;; UUID id, String image, double memory, double cpu, String role, final List<Tuple2<String, String>> env
                 (MesosController$DesiredTask. id type 3000.0 2.0 "*" env-vars)
                 )
               @desired-tasks))
           (shutdownTasks [this]
             (mapv
               (fn [{:keys [agent-id executor-id uuid]}]
                 (Tuple3. agent-id executor-id uuid))
               @to-shutdown))
           )
         client (MesosController/buildClient mesos-uri state-mgr)]
     (reify ContainerSubstrate
       (allInfo [this] {:desired @desired-tasks
                        :current-state @current-state
                        :shutdown @to-shutdown
                        :client client})
       (startService [this type id env-vars monitor]
         (swap! desired-tasks conj {:uuid id :id id :type type :env-vars env-vars})
         )
       (stopService [this id monitor]
         (let [{:keys [state agent-id executor-id] :as current} (get id @current-state)]
           (if (and current
                    (not
                      (#{ContainerSubstrate$TaskState/Stopping
                         ContainerSubstrate$TaskState/RequestedStop
                         ContainerSubstrate$TaskState/Stopped} state)))
             (do
               (swap! to-shutdown conj {:agent-id agent-id
                                        :executor-id executor-id
                                        :uuid id})
               (swap! current-state assoc-in [id :state] ContainerSubstrate$TaskState/RequestedStop)
               ))
           )
         )
       (status [this id]
         (let [{:keys [state info] :as current} (get @current-state id)]
           (if current
             (Tuple2. state (fu/stringify-keys info)))
           )
         ))))
  )

(defn setup-REMOVE
  []
  (def uuid (UUID/randomUUID))
  (def substrate (create-substrate))
  (def client (-> substrate .allInfo :client))
  (defn do-REMOVE [] (def rr (try (-> client .openStream .await) (catch Exception e e))))
  )
;; void update(UUID taskID, TaskState taskState);

;; List<DesiredTask> currentDesiredTasks();

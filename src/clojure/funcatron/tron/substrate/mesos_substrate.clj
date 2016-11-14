(ns funcatron.tron.substrate.mesos-substrate
  (:require [funcatron.tron.options :as f-opts]
            [dragonmark.util.props :as dp]
            [funcatron.tron.util :as fu])
  (:import (funcatron.abstractions ContainerSubstrate ContainerSubstrate$TaskState)
           (funcatron.mesos MesosController MesosController$StateManager)
           (java.net URI)
           (funcatron.helpers Tuple2 Tuple3)))

(defn create-substrate
  "Creates a ContainerSubstrate instance connected to the Mesos cluster"
  ([] (create-substrate (merge @dp/info (:options @f-opts/command-line-options))))

  ([opts]
   (let [mesos-uri (URI. (or (:mesos_uri opts)
                             "http://m1.dcos:5050"))
         desired-tasks (atom [])
         current-state (atom {})
         to-shutdown (atom [])
         state-mgr
         (reify MesosController$StateManager
           (update [this task-id status state]
             (swap! desired-tasks #(into [] (remove (fn [{:keys uuid}] (= uuid task-id)) %)))
             (swap! to-shutdown #(into [] (remove (fn [{:keys uuid}] (= uuid task-id)) %)))
             )
           (currentDesiredTasks [this]
             @desired-tasks)
           (shutdownTasks [this] (mapv
                                   (fn [{:keys [agent-id executor-id uuid]}]
                                     (Tuple3. agent-id executor-id uuid))
                                   @to-shutdown))
           )
         client (MesosController/buildClient mesos-uri state-mgr)]
     (reify ContainerSubstrate
       (startService [this type id env-vars monitor])
       (stopService [this id monitor]
         (let [{:keys [state agent-id executor-id] :as current} (get id @current-state)]
           (if (and current (not
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

;; void update(UUID taskID, TaskState taskState);

;; List<DesiredTask> currentDesiredTasks();



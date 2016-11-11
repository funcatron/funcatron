(ns funcatron.tron.substrate.mesos-substrate
  (:require [funcatron.tron.options :as f-opts]
            [dragonmark.util.props :as dp])
  (:import (funcatron.abstractions ContainerSubstrate)
           (funcatron.mesos MesosController MesosController$StateManager)
           (java.net URI)))

(defn create-substrate
  "Creates a ContainerSubstrate instance connected to the Mesos cluster"
  ([] (create-substrate (merge @dp/info (:options @f-opts/command-line-options))))

  ([opts]
   (let [mesos-uri (URI. (or (:mesos_uri opts)
                             "http://m1.dcos:5050"))
         desired-tasks (atom [])
         current-state (atom {})
         to-shutdown (atom [])
         state-mgr (reify MesosController$StateManager
                     (update [this task-id status state])
                     (currentDesiredTasks [this] @desired-tasks)
                     (shutdownTasks [this] @to-shutdown)
                     )
         client (MesosController/buildClient mesos-uri state-mgr)]
     (reify ContainerSubstrate
       (startService [this type id env-vars monitor])
       (stopService [this id monitor])
       (status [this id]
         (get @current-state id)
         ))))
  )

;; void update(UUID taskID, TaskState taskState);

;; List<DesiredTask> currentDesiredTasks();



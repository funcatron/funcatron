(ns funcatron.tron.substrate.shared
  (:import (funcatron.abstractions ContainerSubstrate)))


(defmulti ^ContainerSubstrate dispatch-substrate
          "A multi-method that dispatches to the right substrate provider"
          (fn [opts] (-> opts :options :substrate_type))
          )
(ns funcatron.tron.store.shared
  (:require [funcatron.tron.options :as the-opts]))

(defmulti dispatch-wire-store "A multi-method that dispatches to the right backing store provider/creator"
          (fn [opts] (-> opts :options :store_type))
          )

(defn wire-up-store
  "Based on the run-time options, wire up an appropriate backing store."
  []
  (dispatch-wire-store @the-opts/command-line-options)
  )
(ns funcatron.tron.brokers.inmemory
  (:require [funcatron.tron.util :as f-util]
            [funcatron.tron.brokers.shared :as shared])
  (:import (funcatron.abstractions MessageBroker)
           (java.util.concurrent ConcurrentHashMap)
           (clojure.lang IAtom)
           (java.util UUID)
           (funcatron.helpers Tuple2)))

(set! *warn-on-reflection* true)

(defn ^MessageBroker create-inmemory-broker
  "Create an in-memory MessageBroker instance. Pass in an atom that contains a Map of queue names."

  ([queue-atom]
   (when (not (map? @queue-atom)) (reset! queue-atom {}))
   (let [listeners (ConcurrentHashMap.)]
     (reify MessageBroker
       (isConnected [this] true)
       (listenToQueue [this queue-name handler]
         (let [atom-holder (atom (atom []))]
           (swap! queue-atom (fn [m]
                               (let [maybe-atom (get m queue-name)]
                                 (if (and (not (nil? maybe-atom))
                                          (instance? IAtom maybe-atom))
                                   (do
                                     (reset! atom-holder maybe-atom)
                                     m
                                     )

                                   (assoc m queue-name @atom-holder)))))
           (let [the-atom @atom-holder
                 uuid (UUID/randomUUID)
                 kill-func (fn [] (remove-watch the-atom uuid))]
             (add-watch the-atom uuid
                        (fn [k r os ns]
                          (future
                            (let [cur @the-atom]
                              (when (not (empty? cur))
                                (let [{:keys [bytes metadata] :as msg} (first cur)]
                                  (reset! the-atom (pop cur))

                                  (let [
                                        message (shared/build-message this metadata bytes (fn [])
                                                               (fn [re-queue] (when re-queue
                                                                                (swap! the-atom conj msg)
                                                                                )))
                                        ]
                                    (try
                                      (.apply handler message)
                                      (.ackMessage message)
                                      (catch Exception e
                                        (do
                                          (.nackMessage message false)
                                          (throw e)))))))))))

             (.put listeners uuid (Tuple2. queue-name kill-func))
             kill-func
             )))

       (sendMessage [this queue-name metadata message]
         (when-let [the-atom (get @queue-atom queue-name)]
           (let [[ct bytes] (f-util/to-byte-array message)
                 metadata (f-util/keywordize-keys metadata)
                 metadata (merge metadata
                                 (when ct {:content-type ct}))
                 ]
             (swap! the-atom conj {:metadata metadata
                                   :bytes    bytes}))))

       (listeners [this]
         (into [] (.values listeners)))

       (close [this] (doseq [^Tuple2 t (.values listeners)]
                       ((._2 t))))))
    ))
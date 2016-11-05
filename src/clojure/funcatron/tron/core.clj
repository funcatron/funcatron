(ns funcatron.tron.core
  (:require [funcatron.tron.routers.shim-router :as shimmy]
            [clojure.tools.logging :as log]
            [funcatron.tron.options :as the-opts]
            [clojure.tools.cli :as cli])

  (:gen-class)
  )

(set! *warn-on-reflection* true)






(defn tryit
  [& args]
  (cli/parse-opts args the-opts/cli-options))


(defn -main
  "The uberjar entrypoint"
  [& args]

  (log/log :info (str "Starting Funcatron. Args: " args))
  (let [opts (cli/parse-opts args the-opts/cli-options)]
    (reset! the-opts/command-line-options opts)
    (log/log :info (str "Argument options: " opts))
    (cond
      (:errors opts)
      (log/log :error (str (:errors opts)))

      (-> opts :options :help)
      (log/log :info (str "Options:\n" (:summary opts)))

      (-> opts :options :devmode)
      (shimmy/start-dev-server)
      )
    ))







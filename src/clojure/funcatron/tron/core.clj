(ns funcatron.tron.core
  (:require [funcatron.tron.modes.dev-mode :as shimmy]
            [taoensso.timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [funcatron.tron.options :as the-opts]
            [funcatron.tron.modes.runner-mode :as runny]
            [funcatron.tron.modes.tron-mode :as tronny]
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

  (info (str "Starting Funcatron. Args: " args))
  (let [opts (cli/parse-opts args the-opts/cli-options)]
    (reset! the-opts/command-line-options opts)
    (info (str "Argument options: " opts))
    (cond
      (:errors opts)
      (error (str (:errors opts)))

      (-> opts :options :help)
      (info (str "Options:\n" (:summary opts)))

      (-> opts :options :devmode)
      (shimmy/start-dev-server)

      (-> opts :options :tron)
      (let [server (tronny/build-tron-from-opts opts)]
        (.startLife server))

      (-> opts :options :runner)
      (let [server (runny/build-runner-from-opts opts)]
        (.startLife server)))))

(ns funcatron.tron.core
  (:require [funcatron.tron.routers.shim-router :as shimmy]
            [clojure.tools.logging :as log]
            [funcatron.tron.options :as the-opts]
            [clojure.tools.cli :as cli])

  (:gen-class)
  )

(set! *warn-on-reflection* true)




(def cli-options
  ;; An option with a required argument
  [[nil "--rabbit_port PORT" "RabbitMQ Port number"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   [nil "--rabbit_host HOSTNAME" "RabbitMQ hostname"
    :assoc-fn (fn [m k v] (update-in m [k] #(into #{} (conj % v))))
    ]

   [nil "--zookeeper_host HOSTNAME" "Zookeeper hostname to Tron Manager Mode"
    :assoc-fn (fn [m k v] (update-in m [k] conj v))
    ]

   [nil "--rabbit_username USERNAME" "RabbitMQ user_name"
    ]

   [nil "--rabbit_password PASSWORD" "RabbitMQ password"
    ]

   [nil "--funcatron_id ID" "The Funcatron ID... used by runners to identify themselves with the Master Tron"
    ;; :default nil
    ]

   [nil "--marathon_host HOSTNAME" "The hostname for the Mesos Marathon Scheduler"]
   [nil "--marathon_port PORT" "The port for the Mesos Marathon Scheduler"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   [nil "--devmode" "Dev Mode"]
   [nil "--runner" "Runner Mode"]
   [nil "--tron" "Manager Tron Mode"]
   #_["-v" nil "Verbosity level"
    :id :verbosity
    :default 0
    :assoc-fn (fn [m k _] (update-in m [k] inc))]
   ;; A boolean option defaulting to nil
   ["-h" "--help" "Print Help and Exit"]])

(defn tryit
  [& args]
  (cli/parse-opts args cli-options))


(defn -main
  "The uberjar entrypoint"
  [& args]

  (log/log :info (str "Starting Funcatron. Args: " args))
  (let [opts (cli/parse-opts args cli-options)]
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







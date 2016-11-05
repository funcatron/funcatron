(ns funcatron.tron.options)


(def cli-options
  ;; An option with a required argument
  [[nil "--rabbit_port PORT" "RabbitMQ Port number"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

   [[nil "--web_port PORT" "Web Server Port number (default 3000)"
     :default 3000
     :parse-fn #(Integer/parseInt %)
     :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

    [[nil "--shim_port PORT" "Dev Mode Shim Server Port number (default 54657)"
      :default 54657
      :parse-fn #(Integer/parseInt %)
      :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]

     [[nil "--dev_request_timeout SECONDS" "How long to wait in dev-mode before timing out a request"
       :default 60
       :parse-fn #(Integer/parseInt %)
       :validate [#(< 0 % 6000) "Must be a number between 1 and 6000"]]

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

      [nil "--tron_queue name" "The name of the queue the Tron listens on"
       :default "for_tron"
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

(def command-line-options
  "An Atom with the command line options"
  (atom {}))
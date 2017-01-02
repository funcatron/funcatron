(ns funcatron.tron.core
  (:require [funcatron.tron.modes.dev-mode :as shimmy]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [clojure.java.shell :as shelly]
            [funcatron.tron.options :as the-opts]
            [funcatron.tron.modes.runner-mode :as runny]
            [funcatron.tron.modes.tron-mode :as tronny]
            [clojure.tools.cli :as cli]
            [funcatron.tron.util :as fu])

  (:gen-class)
  (:import (funcatron.abstractions Lifecycle)
           (java.util Properties)
           (java.io StringReader Reader File)))

(set! *warn-on-reflection* true)

(def version-info
  (or
    ;; try getting the uberjar info
    (try
      (let [infos (enumeration-seq
                    (.getResources
                      (.getClassLoader
                        (.getClass (fn [])))
                      "META-INF/maven/funcatron/tron/pom.properties"))]
        (first
          (for [i infos]
            (let [s (.replace ^String (slurp i) "\\n" "")
                  p (Properties.)]
              (.load p (StringReader. s))
              (fu/kebab-keywordize-keys p)
              ))))
      (catch Exception e (do
                           (info e "Failed to load JAR properties")
                           nil)))

    (try
      {:version     (nth (read-string (slurp (File. "project.clj"))) 2)
       :revision    (.trim ^String (:out (shelly/sh "git" "rev-parse" "HEAD")))
       :group-id    "funcatron",
       :artifact-id "tron"}
      (catch Exception e (do
                           (info e "Failed to load local properties")
                           nil)))

    ;; no idea
    {:version "UNKNOWN",
     :revision "UNKNOWN",
     :group-id "funcatron",
     :artifact-id "tron"}))

(defn- ordered-tron-env
  "Return the elements of the map that begin with TRON_ ordered by their number"
  [m]
  (->>
    m
    (filter (fn [[k _]] (.startsWith ^String k "TRON_")))
    (map (fn [[^String k v]] [(-> k (.substring 5) read-string) v]))
    (sort-by first)
    (map second)
    (into [])))

(defn -main
  "The uberjar entrypoint"
  [& args]


  (timbre/merge-config!
    {:middleware
     [(fn [d]  (let [hn (:hostname_ d)]
                 (merge d {:hostname_
                           (delay
                             (str @hn " # "
                                  (:version version-info)
                                  "-"
                                  (:revision version-info)))})))]})

  (info (str "Starting Funcatron. Args: " args))
  (info (str "Version " version-info))
  (info (str "Env Vars" (System/getenv)))
  (let [opts (merge
               (cli/parse-opts args the-opts/cli-options)
               (cli/parse-opts (ordered-tron-env (System/getenv)) the-opts/cli-options)
               )]
    (info "Computed command line options: " opts)
    (reset! the-opts/command-line-options opts)
    (trace (str "Argument options: " opts))
    (cond
      (:errors opts)
      (do
        (error (str (:errors opts)))
        (fu/graceful-exit 0)
        )

      (-> opts :options :dump)
      (do
        (info (str "Env " (System/getenv)))
        (info (str "Props " (System/getProperties)))
        (info (str "DNS records for the RabbitMQ instance: " (fu/dns-lookup "_rabbit._rabbit-funcatron._tcp.marathon.mesos")))
        (fu/graceful-exit 0))

      (-> opts :options :help)
      (do
        (info (str "Options:\n" (:summary opts)))
        (fu/graceful-exit 0))

      (fu/dev-mode? opts)
      (shimmy/start-dev-server)

      (fu/tron-mode? opts)
      (do
        (info "Starting Tron mode")
        (info "")
        (let [server (tronny/build-tron-from-opts opts)]
          (.startLife server)))

      (fu/runner-mode? opts)
      (let [server (runny/build-runner-from-opts opts)]
        (.startLife server)))))

(defn test-start-tron
  []
  (let [my-tron (tronny/build-tron-from-opts)]
    (def ^Lifecycle tron my-tron)
    (.startLife my-tron)
    my-tron)

  )

(defn test-start-runner
  []
  (let [my-r (runny/build-runner-from-opts)]
    (def ^Lifecycle runner my-r)
    (.startLife my-r)
    my-r)
  )

(defn start-both
  []
  (test-start-tron)
  (test-start-runner))

(defn start-dev
  []
  )
(ns funcatron.tron.modes.tron-mode
  (:require [funcatron.tron.util :as fu]
            [clojure.java.io :as cio]
            [compojure.core :refer [GET defroutes POST]]
            [compojure.route :refer [not-found resources]])
  (:import (java.io File FileOutputStream)))



(defonce ^:private shutdown-server (atom nil))

(defn- upload-func-bundle
  "Get a func bundle"
  [{:keys [body] :as req}]
  (if body
    (let [file (File/createTempFile "func-a-" ".tron")]
      (println "Body " body)
      (println (:headers req))
      (cio/copy body file)
      (let [sha (fu/sha256 file)
            sha (fu/base64encode sha)]
        {:status 200 :body sha})
      )
    {:status 403 :body "Must post data"}
    )
  )

(defroutes tron-routes
           "Routes for Tron"
           (POST "/api/v1/add_func"
                 req (upload-func-bundle req)))

(defn ring-handler
  "Handle http requests"
  [req]

  (tron-routes req)
  )

(defn- connect-to-message-queue
  []
  )


(defn start-tron-server
  "Start the Tron mode server"
  []
  (fu/run-server #'ring-handler shutdown-server)
  (connect-to-message-queue))
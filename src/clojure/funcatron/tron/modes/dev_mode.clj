(ns funcatron.tron.modes.dev-mode
  (:gen-class)
  (:require [funcatron.tron.util :as fu]
            [cheshire.core :as json]
            [org.httpkit.server :as kit]
            [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [funcatron.tron.options :as the-opts]
            [io.sarnowski.swagger1st.context :as s1ctx])
  (:import (java.net ServerSocket Socket SocketException)
           (java.util Base64 UUID)
           (java.io BufferedWriter OutputStreamWriter BufferedReader InputStreamReader OutputStream InputStream ByteArrayInputStream IOException)))


(set! *warn-on-reflection* true)

(defonce ^:private shim-socket (atom nil))

(defonce ^:private sync-obj (Object.))





(defn- shutdown-socket
  "Close the server-socket"
  []
  (locking sync-obj
    (let [ss @shim-socket]
      (swap! shim-socket #(-> %
                              (dissoc ::swagger)
                              (dissoc ::input)
                              (dissoc ::output)
                              (dissoc ::socket)))
      (when-let [inputa (::input ss)]
        (doseq
          [input [inputa]]
          (try
            (.close ^InputStream input)
            (catch Exception e nil)))
        )
      (when-let [outputs (::output ss)]
        (doseq [output [outputs]]
          (try
            (.close ^OutputStream output)
            (catch Exception e nil)))
        )
      (when-let [sockets (::socket ss)]
        (doseq [socket [sockets]]
          (try
            (.close ^Socket socket)
            (catch Exception e nil)))
        )
      )))

(defn send-message
  [message]
  (let [b64 (fu/encode-body message)]
    (try
      (doseq [out-x (filter identity [(::output @shim-socket)])]
        (let [out (BufferedWriter. (OutputStreamWriter. ^OutputStream out-x "UTF-8"))]
          (locking sync-obj
            (.write out b64)
            (.write out "\n")
            (.flush out))))
      (catch IOException io
        (do
          (clojure.tools.logging/log :error io "Failed to send message")
          (shutdown-socket)
          ))
      )))

(defn- shutdown
  "Close the server-socket"
  []
  (locking sync-obj
    (let [ss @shim-socket]
      (reset! shim-socket nil)
      (when-let [inputa (::input ss)]
        (doseq
          [input [inputa]]
          (try
            (.close ^InputStream input)
            (catch Exception e nil)))
        )
      (when-let [outputs (::output ss)]
        (doseq [output [outputs]]
          (try
            (.close ^OutputStream output)
            (catch Exception e nil)))
        )
      (when-let [sockets (::socket ss)]
        (doseq [socket [sockets]]
          (try
            (.close ^Socket socket)
            (catch Exception e nil)))
        )
      (when-let [ss (::server-socket ss)]
        (try
          (.close ^ServerSocket ss)
          (catch Exception e nil))))))

(defn- set-swagger
  [swagger-str]
  (swap! shim-socket dissoc ::swagger)
  (when-let [sd (try
                  (s1ctx/load-swagger-definition
                    :yaml
                    swagger-str
                    )
                  (catch Exception e1
                    (try
                      (s1ctx/load-swagger-definition
                        :json
                        swagger-str
                        )
                      (catch Exception e2
                        (do
                          (clojure.tools.logging/log :error e1 "Failed to parse as YAML")
                          (clojure.tools.logging/log :error e2 "Failed to parse as JSON")
                          nil
                          )))))]
    (swap! shim-socket assoc ::swagger sd)
    ))

(defn do-reply
  [{:keys [replyTo] :as resp}]
  (when-let [p (get-in @shim-socket [::requests replyTo])]
    (deliver p resp)
    (swap! shim-socket #(update % ::requests dissoc replyTo))
    )
  )

(defn- listen-to-input
  "Listen to the incoming socket"
  [^InputStream input]
  (let [in (BufferedReader. (InputStreamReader. input "UTF-8"))
        continue (atom true)]
    (try
      (while @continue
        (let [line (.readLine in)]
          (if line
            (let [bytes (.decode (Base64/getDecoder) line)
                  map (json/parse-stream (BufferedReader. (InputStreamReader. (ByteArrayInputStream. bytes) "UTF-8")))
                  map (fu/keywordize-keys map)
                  cmd (:cmd map)]
              (cond
                (= "setSwagger" cmd)
                (set-swagger (:swagger map))

                (= "hello" cmd)
                (clojure.tools.logging/log :info "We got hello from the client")

                (= "reply" cmd)
                (do-reply map)

                :else nil
                )
              )
            (reset! continue false)
            )))
      (finally
        (shutdown-socket)
        )
      )))

(defn- listen-to-socket
  "Accept socket connections"
  [^ServerSocket server-socket]
  (shutdown)
  (reset! shim-socket {::server-socket server-socket
                       ::socket        nil
                       ::input         nil
                       ::requests      {}
                       ::output        nil})
  (try
    (while (not (.isClosed server-socket))
      (let [socket (.accept server-socket)
            input (.getInputStream socket)
            output (.getOutputStream socket)
            thread (Thread. (fn [] (listen-to-input input)) "Input Listener")]
        (swap! shim-socket #(merge % {::socket socket
                                      ::input  input
                                      ::output output}))
        (.start thread)
        )

      )
    (catch SocketException se nil)                          ;; don't worry about closed socket closing
    (catch Exception e (clojure.tools.logging/log :error e "Closed server socket"))
    (finally
      (shutdown)))
  )

(defn exec-app
  [op-i req]
  (clojure.tools.logging/log :info "Dispatching request to " op-i)
  (let [req2 (dissoc req :swagger)
        req2 (assoc req2 :body (get-in req [:parameters :body]))]
    (let [uuid (.toString (UUID/randomUUID))
          p (promise)]
      (swap! shim-socket assoc-in [::requests uuid] p)
      (send-message {:cmd     "invoke"
                     :headers (:headers req)
                     :class   op-i
                     :replyTo uuid
                     :body    (if (:body req2) (json/generate-string (:body req2)) nil)})
      (clojure.tools.logging/log :info "Sent the request over the wire to the IDE/App ")
      (let [answer (deref p (* 1000 (-> @the-opts/command-line-options :options :dev_request_timeout)) ::failed)]
        (if (= ::failed answer)
          (do
            (clojure.tools.logging/log :info "Timed Out")
            {:status 500 :headers {"Content-Type" "text/plain"} :body "Didn't get the answer"})
          (let [response (:response answer)
                response (update response :headers fu/stringify-keys)
                response (if (and (:body response) (:decodeBody answer))
                           (assoc response
                             :body
                             (ByteArrayInputStream. (.decode (Base64/getDecoder) ^String (:body response))))
                           response)
                ]
            (clojure.tools.logging/log :info "Got a response. Status code " (:status response))
            response
            ))))))

(defn resolve-app
  [req]
  (let [operationId (get req "operationId")]
    (partial exec-app operationId)
    ))

(defn make-app
  [swagger]
  (-> {:definition     swagger
       :chain-handlers (list)}
      (s1st/discoverer)
      (s1st/mapper)
      (s1st/parser)
      (s1st/protector {"oauth2" (s1stsec/allow-all)})
      (s1st/executor :resolver resolve-app))
  )

(defn setup
  "Set up a listner from a Shim"
  [^long port]
  (locking sync-obj
    (shutdown)
    (let [socket (ServerSocket. port)
          thread (Thread. (fn [] (listen-to-socket socket)) "Socket Acceptor")]
      (.start thread)
      )
    )
  )

(defn http-handler
  [req]
  (clojure.tools.logging/log :info "Incoming request to " (:uri req))
  (if-let [swagger (::swagger @shim-socket)]
    (let [app (make-app swagger)
          resp (app req)]
      resp
      )
    (do
      (clojure.tools.logging/log :info "No Connection from dev-shim or no Swagger file defined")
      {:status 404 :headers {"Content-Type" "text/plain"} :body "No Swagger Defined. Unable to route request\n"})
    ))

(defonce end-server (atom nil))

(defn run-server
  []
  (reset! end-server
          (kit/run-server #'http-handler {:port (-> @the-opts/command-line-options :options :web_port)})))

(defn start-dev-server
  "The dev server entrypoint"
  []
  (run-server)
  (setup (-> @the-opts/command-line-options :options :shim_port))
  (clojure.tools.logging/log :info "Your Funcatron Dev Server is running. Point your dev-shim at port 54657 and your browser at http://localhost:3000")
  )

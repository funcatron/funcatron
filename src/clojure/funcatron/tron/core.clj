(ns funcatron.tron.core
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [clojure.spec :as s]
            [langohr.basic :as lb])
  (:gen-class)
  )

(set! *warn-on-reflection* true)


(def sample-swagger
  "swagger: '2.0'

info:
   title: Example API
   version: '0.1'

basePath: /foo

host: localfoo

paths:
   /helloworld:
     get:
       summary: Returns a greeting.
       operationId: tron.core/generate-greeting
       parameters:
         - name: firstname
           in: query
           required: true
           type: string
           pattern: \"^[A-Z][a-z]+\"
       responses:
           200:
               description: say hello
")

(defn generate-greeting
  [req]

  ;; (clojure.pprint/pprint req)
  {:foo "bar" "name" (-> req :query-params (get "firstname"))}
  )



#_(defn my-func
      [handler]
      (fn [req]
        (let [resp (handler req)]
          (println "Response:\n" resp)
          resp
          )

        )
      )







#_(defn handle-delivery
  [ch metadata payload]

  (let [payload (parse-and-fix-payload payload)
        ring-request (make-ring-request payload)
        app (make-app (-> (tj/qq)))
        resp (app ring-request)
        ]



    (let [body (:body resp)
          body (cond
                 (instance? String body )
                 (.encodeToString (Base64/getEncoder) (.getBytes ^String body "UTF-8"))

                 (instance? InputStream body)
                 (.encodeToString (Base64/getEncoder) (IOUtils/toByteArray ^InputStream body))

                 :else
                 (.encodeToString (Base64/getEncoder) (.getBytes (json/generate-string body) "UTF-8"))
                 )]
      (lb/publish ch "" (:reply-to metadata) (.getBytes (json/generate-string (assoc resp :body body)) "UTF-8"))))
  )










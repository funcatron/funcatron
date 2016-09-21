(ns funcatron.tron.dispatch
  (:import (java.io ByteArrayInputStream)))

(defn remove-jar-info
  "Removes the specific jar-info from the collection"
  [])


(defn make-ring-request
  "Takes an OpenResty style request and turns it into a Ring style request"
  [req]
  {:server-port    (read-string (or (:server_port req) "80"))
   :server-name    (:host req)
   :remote-addr    (or (-> req :headers (get "x-remote-addr"))
                       (-> req :remote_addr)
                       )
   :uri            (:uri req)
   :open-resty     req
   :query-string   (:args req)
   :scheme         (:scheme req)
   :request-method (.toLowerCase ^String (:method req))
   :protocol       (:server_protocol req)
   :headers        (:headers req)
   :body           (when-let [^"[B" body (:body req)]
                     (when (> (count body) 0)
                       (ByteArrayInputStream. body)))
   }
  )

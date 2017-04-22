(ns funcatron.sample.clojure.funcs
  (:gen-class)
  (:import (funcatron.intf Func Context MetaResponse)
           (java.util Date Random Map)
           (java.util.logging Level)))

(set! *warn-on-reflection* true)

(defn simple_get
  "Handle the incoming simple request"
  [_ ^Context c]
  (-> c
      .getLogger
      (.info "In Simple Get... Clojure style"))
  (let [num (some-> c
                    .getMergedParams
                    (.get "num"))
        num-map (if num {"num" num} {})
        ret (merge
              {"query-params" (some-> c .getRequestInfo (.get "query-params"))
               "time"         (.toString (Date.))
               "bools" true
               "numero" (.nextDouble (Random.))}
              num-map)
        ]
    (-> c
        .getLogger
        (.log Level/INFO "Returning" ret))
    ret))


(defn post_or_delete
  "Handle the post, delete or other apply"
  [{name "name" age "age"} ^Context c]
  (let [^Number cnt (some-> c
                    .getRequestParams
                    ^Map (.get "path")
                    (.get "cnt"))]
    (case (.getMethod c)
      "delete" {"name" (str "Deleted " cnt)
                "age" cnt}
      "post" (->>
               (range 1 (inc cnt))
               (mapv (fn [i] {"name" (str name i)
                             "age" (+ age i)})))
      (reify MetaResponse
        (getResponseCode [_] 400)
        (getContentType [_] "text/plain")
        (getBody [_] (.getBytes (str "Expecting a POST or DELETE, but got " (.getMethod c)) "UTF-8"))))
    ))

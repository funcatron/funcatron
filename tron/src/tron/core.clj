(ns tron.core
  (:require [langohr.core :as lc]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.consumers :as lcons]
            [langohr.basic :as lb])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(println "Hello")

(select-keys {:a "foo" :b "dog"} [:b])

(defn handle-delivery
  "Handles message delivery"
  [ch metadata payload]
  (println (format " [x] Received %s" (String. payload "UTF-8"))))

(with-open [conn (lc/connect)]
  (let [ch   (lch/open conn)]
    (lq/declare ch "hello" {:durable false :auto-delete false})
    (println " [*] Waiting for messages. To exit press CTRL+C")
    (lcons/blocking-subscribe ch "hello" handle-delivery {:auto-ack true})))


(with-open [conn (lc/connect)]
  (let [ch   (lch/open conn)]
    (lq/declare ch "hello" {:durable false :auto-delete false})
    (lb/publish ch "" "hello" (.getBytes "Hello World!" "UTF-8"))
    (println " [x] Sent 'Hello World!'")))

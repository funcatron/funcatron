(ns funcatron.tron.routers.python-router
  (:import (net.razorvine.pyro PyroURI PyroProxy)))


(defn proxy-for-port
  "Get a Pyro Proxy given the port or host and port"
  ([port] (proxy-for-port "localhost" port))
  ([host port]
    (let [uri (PyroURI. (str "PYRO:funcatron.main@" host ":" port))
          proxy (PyroProxy. uri)]
      proxy
      )))


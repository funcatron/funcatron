(ns funcatron.tron.util
  "Utilities for Tron"
  (:require [cheshire.core :as json]
            [clojure.spec :as s])
  (:import (cheshire.prettyprint CustomPrettyPrinter)))

(def ^CustomPrettyPrinter pretty-printer
  "a JSON Pretty Printer"
  (json/create-pretty-printer json/default-pretty-print-options))
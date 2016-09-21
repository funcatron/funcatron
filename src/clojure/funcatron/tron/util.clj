(ns funcatron.tron.util
  (:require [cheshire.core :as json]))

(def pretty-printer
  (json/create-pretty-printer json/default-pretty-print-options))
(ns ansel.core
  (:require [ansel.server :refer [start-server]]
            [ansel.db :refer [init]])
  (:gen-class))

(defn -main
  [& args]
  (init)
  (start-server (Integer. (or (first args) "8000"))))

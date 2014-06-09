(ns ansel.core
  (:require [ansel.server :refer [start-server]]
            [ansel.db :refer [start-saving init]])
  (:gen-class))

(defn -main
  [& args]
  (init)
  (start-saving)
  (start-server (Integer. (or (first args) "8000"))))

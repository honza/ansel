(ns ansel.core
  (:require [ansel.server :refer [start-server]]
            [ansel.db :refer [start-saving]]))

(defn -main
  [& args]
  ;; (start-saving)
  (start-server))

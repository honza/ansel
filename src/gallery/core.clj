(ns gallery.core
  (:require [gallery.server :refer [start-server]]
            [gallery.db :refer [start-saving]]))

(defn -main
  [& args]
  ;; (start-saving)
  (start-server))

(ns ansel.server
  (:require [taoensso.timbre :refer [info]]
            [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ansel.session :refer [filesystem-store]]
            [ansel.urls :refer [server-routes]]))

(def server
  (-> server-routes
      (site {:session {:store (filesystem-store "session.json")}})))

(defn start-server [port]
  (run-jetty server {:port port :join? false})
  (info "server online"))

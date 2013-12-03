(ns ansel.server
  (:require [taoensso.timbre :refer [info]]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.session :refer [wrap-session]]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [ansel.db :as db]
            [ansel.session :refer [filesystem-store]]
            [ansel.views :as views]))

(defn credential-fn [auth-map]
  (let [keyword-map (update-in auth-map [:username] keyword)]
    (creds/bcrypt-credential-fn @db/users keyword-map)))

(def server
  (-> views/server-routes
    (friend/authenticate {:allow-anon? true
                          :login-uri "/login"
                          :default-landing-url "/"
                          :credential-fn credential-fn
                          :workflows [(workflows/interactive-form)]})
    (handler/site {:session {:store (filesystem-store "session.json")}})))

(defn start-server [port]
  (run-jetty server {:port port :join? false})
  (info "server online"))

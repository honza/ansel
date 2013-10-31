(ns ansel.server
  (:require [clojure.string :as s]
            [taoensso.timbre :refer [info]]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as resp]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [selmer.parser :refer [render-file]]
            [ansel.db :as db]
            [ansel.util :refer [cwd pretty-json]]))

(selmer.parser/set-resource-path! (or (get-in @db/db [:config :template-path])
                                      (str (cwd) "/resources/templates/")))
(selmer.parser/cache-off!)

(defn render
  ([t]   (render-file t {}))
  ([t c] (render-file t c)))

(defn process-uploaded-file [f]

  )

(defroutes server-routes
  (GET "/" req (render "index.html" (friend/identity req)))
  (GET "/login" [] (render "login.html"))
  (GET "/logout" req (friend/logout* (resp/redirect "/")))
  (GET "/signup" [] (render "signup.html"))
  (POST "/signup" {{:keys [username password confirm] :as params} :params :as req}
        (if (and (not-any? s/blank? [username password confirm])
                 (= password confirm))
          (let [user (select-keys params [:username :password])]
            (db/add-user-to-db user)
            (friend/merge-authentication (resp/redirect "/") user))
          (assoc (resp/redirect (str (:context req) "/")) :flash "passwords don't match!")))

  ;; (GET "/upload" req
  ;;      (friend/authenticated (render "upload.html")))
  (GET "/upload" req
       (render "upload.html"))

  (POST "/upload" req
        (println (get-in req [:params :files]))
        ;; (render "upload.html")

        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (pretty-json
        {:files [
                 
                 {:name "chair.jpg"
                  :size 815469
                  :url "http://localhost:3000/chair.jpg"
                  :thumbnailUrl "http://localhost:3000/chair.jpg"}
                 
                 ]})})

  (route/resources "/")
  (route/not-found "Not Found"))

(defn credential-fn [auth-map]
  (let [keyword-map (update-in auth-map [:username] keyword)]
    (creds/bcrypt-credential-fn @db/users keyword-map)))

(def server
  (handler/site 
    (friend/authenticate server-routes
                         {:allow-anon? true
                          :login-uri "/login"
                          :default-landing-url "/"
                          :credential-fn credential-fn
                          :workflows [(workflows/interactive-form)]})))

(defn start-server []
  (run-jetty server {:port 8000 :join? false})
  (info "server online"))

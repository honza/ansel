(ns ansel.server
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
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
            [ansel.exif :refer [read-exif get-captured-timestamp]]
            [ansel.util :refer [cwd pretty-json]]))

(selmer.parser/set-resource-path! (or (get-in @db/db [:config :template-path])
                                      (str (cwd) "/resources/templates/")))
(selmer.parser/cache-off!)

(defn render
  ([t]   (render-file t {}))
  ([t c] (render-file t c)))

(defn process-uploaded-file [f]
  (let [uploads (or (get-in @db/db [:config :upload-path])
                    (str (cwd) "/resources/public/uploads/"))
        filename (:filename f)
        exif (read-exif (:tempfile f))
        captured (get-captured-timestamp exif)
        photo {:filename filename
               :capture captured
               :caption nil}]

    (io/copy (:tempfile f) (io/file (str uploads filename)))
    (db/add-photo-to-db photo)

    {:name filename
     :url (str "/uploads/" filename)
     :thumbnailUrl (str "/uploads/" filename)
     }))

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
    (let [uploaded (get-in req [:params :files])]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (pretty-json
                 {:files (map process-uploaded-file uploaded)})}))

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

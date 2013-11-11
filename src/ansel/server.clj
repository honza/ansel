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
            [ansel.resize :as r]
            [ansel.exif :refer [read-exif format-exif get-captured-timestamp]]
            [ansel.util :refer [pretty-json]]))

(when-let [p (db/get-template-path)]
  (selmer.parser/set-resource-path! p))

(selmer.parser/cache-off!)

(defn add-thumbs-to-photo [p]
  (let [small (r/get-thumb-name (:filename p) 200)
        big (r/get-thumb-name (:filename p) 900)]
  (assoc p :small-thumb small :big-thumb big)))

(defn add-thumb-urls [db]
  (let [thumbed (map add-thumbs-to-photo (:images db))]
    (assoc db :images thumbed)))

(defn sort-images [db]
  (assoc db :images (reverse (sort-by :filename (:images db)))))

(defn sort-albums [db]
  (assoc db :albums (reverse (sort-by :name (:albums db)))))

(defn unroll-images [db]
  (assoc db :images (vals (:images db))))

(defn unroll-albums [db]
  (assoc db :albums (vals (:albums db))))

(defn prepare-db [db]
  (-> db
      unroll-images
      unroll-albums
      sort-images
      sort-albums
      add-thumb-urls))

(defn render
  ([t]
   (render-file t (prepare-db (db/get-context))))
  ([t c]
   (render-file t (merge c (prepare-db (db/get-context))))))

(defn process-uploaded-file [album f]
  (let [uploads (db/get-uploads-path)
        filename (:filename f)
        raw-exif (read-exif (:tempfile f))
        exif (format-exif raw-exif)
        photo {:filename filename
               :captured (:captured exif)
               :exif exif
               :albums (if album [album] [])
               :caption nil}
        _ (io/copy (:tempfile f) (io/file (str uploads filename)))
        file-upload-path (str uploads filename)
        small-thumb-url (str "/thumbs/"
                             (r/make-thumb
                               (io/file file-upload-path)
                               200))
        big-thumb-url (str "/thumbs/"
                             (r/resize-to-width*
                               (io/file file-upload-path)
                               900))]

    (db/add-photo-to-db photo)
    (db/add-album-to-db album)

    {:name filename
     :url (str "/uploads/" filename)
     :thumbnailUrl small-thumb-url}))

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
    (let [uploaded         (get-in req [:params :files])
          album            (get-in req [:params :album])
          process-uploaded (partial process-uploaded-file album)]
      {:status 200
        :headers {"Content-Type" "application/json"}
        :body (pretty-json
                {:files (map process-uploaded uploaded)})}))

  (GET "/image/:image" req
    (let [image-name (get-in req [:params :image])
          image      (@db/images (keyword image-name))]
      (render "single.html" {:image (add-thumbs-to-photo image)})))

  (POST "/like/:image" req
    (let [image-name (get-in req [:params :image])
          image      (@db/images (keyword image-name))]
      ;; (render "single.html" {:image (add-thumbs-to-photo image)})
      (db/add-like-to-photo image
      ))

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

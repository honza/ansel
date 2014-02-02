(ns ansel.views
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [selmer.parser :refer [render-file]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [jordan.core :refer :all]
            [ansel.resize :as r]
            [ansel.db :as db]
            [ansel.exif :refer [read-exif]]
            [ansel.util :refer [pretty-json in? slugify safe-subvec paginate]]))

(when-let [p (db/get-template-path)]
  (selmer.parser/set-resource-path! p))

(selmer.parser/cache-off!)

(defn authenticated? [req]
  (get-in req [:session :user]))

(defn administrator? [req]
  (get-in req [:session :user :admin]))

(defn default-error [req]
  {:status 404
   :body "not found"})

(reset! logged-in-fn authenticated?)
(reset! admin-fn administrator?)
(reset! default-404 default-error)

(def page-size 20)

(defn render
  ([req t]
   (render req t {}))
  ([req t ctx]
   (let [db (or (:db ctx) (db/get-db))
         user {:user (get-in req [:session :user])}]
      (render-file t (merge db (dissoc ctx :db) user)))))

(defn make-photo [filename exif album]
  {:filename filename
   :captured (:captured exif)
   :exif exif
   :albums (if album [album] [])
   :caption nil})

(defn process-uploaded-file [album f]
  (let [filename (:filename f)
        exif (read-exif (:tempfile f))
        uploaded-file (io/file (str (db/get-uploads-path) filename))]
    (io/copy (:tempfile f) uploaded-file)
    (db/add-photo-to-db (make-photo filename exif album))
    (db/add-album-to-db {:name album :cover nil :slug (slugify album)})
    {:name filename
     :url (r/thumb-url (r/resize-to-width* uploaded-file 900))
     :thumbnailUrl (r/make-small-thumb uploaded-file)}))

;; Routes ---------------------------------------------------------------------


(defn handle-login [req]
  (let [{{:keys [username password]} :params} req
        user (db/get-user (or username nil))]
    (if user
      (if (db/verify-bcrypt password (:password user))
        (-> (resp/redirect "/")
            (assoc :session {:user user}))
        (default-error req))
      (default-error req))))

(defroutes server-routes
  (GET "/" req (render req "index.html"))
  (GET "/login" req (render req "login.html"))
  (POST "/login" req (handle-login req))

  (GET "/logout" req (-> (resp/redirect "/")
                         (assoc :session {})))
  (GET "/signup" req (render req "signup.html" ))
  (POST "/signup" {{:keys [username password confirm] :as params} :params :as req}
        (if (and (not-any? s/blank? [username password confirm])
                 (= password confirm))
          (let [user (select-keys params [:username :password])]
            (db/add-user-to-db user)
            (resp/redirect "/"))
          (assoc (resp/redirect (str (:context req) "/")) :flash "passwords don't match!")))

  (with-admin-required
    (GET "/upload" req (render req "upload.html")))

  (with-admin-required
    (POST "/upload" req
      (let [uploaded         (get-in req [:params :files])
            album            (get-in req [:params :album])
            process-uploaded (partial process-uploaded-file album)]
        {:status 200
          :headers {"Content-Type" "application/json"}
          :body (pretty-json
                  {:files (map process-uploaded uploaded)})})))

  (GET "/image/:image" req
    (let [image-name (get-in req [:params :image])
          image      (get @db/images (keyword image-name))
          user       (get-in req [:session :user])
          comments   (db/get-comments-for-photo (keyword image-name))
          you-like   (when user
                       (in? (:likes image) (:username user)))]
      (if image
        (render req "single.html" {:image (db/add-thumbs-to-photo image)
                                   :comments comments
                                   :you-like you-like})
        (default-error req))))

  (with-login-required
    (POST "/image/:image" req
      (let [image-name (get-in req [:params :image])
            image (@db/images (keyword image-name))
            username (get-in req [:session :user :username])
            c (get-in req [:params :comment])]
        (db/comment-on-photo image username c)
        (resp/redirect (str (:context req) "/image/" image-name)))))

  (with-login-required
    (POST "/like" req
      (let [image-name (get-in req [:params :image])
            image      (@db/images (keyword image-name))
            username   (get-in req [:session :user :username])]
        (when-not (in? (:likes image) username)
          (db/add-photo-to-db
            (update-in image [:likes] conj username)))
        (resp/redirect (str (:context req) "/image/" image-name)))))

  (GET "/album" req
    (render req "album-form.html" {:next (or (get-in req [:params :next])
                                             "/albums")}))

  (with-admin-required
    (POST "/album" req
      (let [album (get-in req [:params :album])
            redir (get-in req [:params :next])]
        (db/add-album-to-db {:name album :cover nil :slug (slugify album)})
        (resp/redirect (str (:context req) redir)))))

  (GET "/albums" req
    (render req "albums.html"))

  (GET "/albums/:album" req
    (let [album-name (get-in req [:params :album])
          album      (@db/albums (keyword album-name))
          all-images (map db/add-thumbs-to-photo (vals @db/images))
          full       (db/add-images-to-album all-images album)]
      (render req "album.html" {:album full})))

  (GET "/all" req
    (let [c (db/get-db)
          images (take page-size (:images c))]
      (render req "images.html" {:db c
                                 :next (when (> (count (:images c)) page-size)
                                         2)
                                 :images images})))

  (GET "/all/:page" req
    (let [c (db/get-db)
          images (vec (:images c))
          page (Integer. (get-in req [:params :page]))
          [start end] (paginate page page-size images)
          images (safe-subvec images start end)]
      (if images
        (render req "images.html" {:db c
                                   :page page
                                   :next (inc page)
                                   :prev (dec page)
                                   :images images})
        (resp/redirect (str (:context req) "/all")))))

  (route/files "/thumbs" {:root (db/get-thumbs-path)})
  (route/resources "/")
  (route/not-found "Not Found"))

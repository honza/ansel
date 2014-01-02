(ns ansel.views
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.math.numeric-tower :refer [ceil]]
            [selmer.parser :refer [render-file]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [cemerick.friend :as friend]
            [ansel.resize :as r]
            [ansel.db :as db]
            [ansel.exif :refer [read-exif]]
            [ansel.util :refer [pretty-json in? slugify]]))

(when-let [p (db/get-template-path)]
  (selmer.parser/set-resource-path! p))

(selmer.parser/cache-off!)

(def page-size 20)

(defn subvec*
  "Safer subvec"
  [v start end]
  (when (or start end)
    (subvec v start end)))

(defn paginate [page page-size images]
  (when (pos? page)
    (let [page-count (int (ceil (/ (count images) page-size)))]
      (when (>= page-count page)
        (if (= page 1)
          [0 (min (dec page-size)
                  (count images))]
          [(* page-size (dec page))
           (min (dec (* page-size page))
                (count images))])))))

(defn render
  ([req t]
   (render req t {}))
  ([req t ctx]
   (let [ident (friend/identity req)
         db (or (:db ctx) (db/get-db))
         roles (get-in ident [:authentications (:current ident) :roles])
         admin? {:is-admin? (in? roles "admin")}]
    (render-file t (merge db (dissoc ctx :db) ident admin?)))))

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

(defroutes server-routes
  (GET "/" req (render req "index.html"))
  (GET "/login" req (render req "login.html"))
  (GET "/logout" req (friend/logout* (resp/redirect "/")))
  (GET "/signup" req (render req "signup.html" ))
  (POST "/signup" {{:keys [username password confirm] :as params} :params :as req}
        (if (and (not-any? s/blank? [username password confirm])
                 (= password confirm))
          (let [user (select-keys params [:username :password])]
            (db/add-user-to-db user)
            (friend/merge-authentication (resp/redirect "/") user))
          (assoc (resp/redirect (str (:context req) "/")) :flash "passwords don't match!")))

  (GET "/upload" req
    (friend/authorize #{"admin"} (render req "upload.html")))

  (POST "/upload" req
    (friend/authorize #{"admin"}
      (let [uploaded         (get-in req [:params :files])
            album            (get-in req [:params :album])
            process-uploaded (partial process-uploaded-file album)]
        {:status 200
          :headers {"Content-Type" "application/json"}
          :body (pretty-json
                  {:files (map process-uploaded uploaded)})})))

  (GET "/image/:image" req
    (let [image-name (get-in req [:params :image])
          image      (@db/images (keyword image-name))
          ident      (friend/identity req)]
      (render req "single.html"
              (merge
                {:image (db/add-thumbs-to-photo image)
                 :you-like (in? (:likes image) (:current ident))}
                ident))))

  (POST "/like" req
    (friend/authenticated
      (let [image-name (get-in req [:params :image])
            image      (@db/images (keyword image-name))
            username   (:current (friend/identity req))]
        (when-not (in? (:likes image) username)
          (db/add-photo-to-db
            (update-in image [:likes] conj username)))
        (resp/redirect (str (:context req) "/image/" image-name)))))

  (GET "/album" req
    (render req "album-form.html" {:next (or (get-in req [:params :next])
                                             "/albums")}))

  (POST "/album" req
    (let [album (get-in req [:params :album])
          redir (get-in req [:params :next])]
      (db/add-album-to-db {:name album :cover nil :slug (slugify album)})
      (resp/redirect (str (:context req) redir))))

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
          images (subvec* images start end)]
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

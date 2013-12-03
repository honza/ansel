(ns ansel.views
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [selmer.parser :refer [render-file]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [cemerick.friend :as friend]
            [ansel.resize :as r]
            [ansel.db :as db]
            [ansel.exif :refer [read-exif]]
            [ansel.util :refer [pretty-json in?]]))

(when-let [p (db/get-template-path)]
  (selmer.parser/set-resource-path! p))

(selmer.parser/cache-off!)

(defn render
  ([req t]
   (render req t {}))
  ([req t c]
   (let [ident (friend/identity req)
         current (:current ident)
         roles   (get-in ident [:authentications current :roles])]
    (render-file t (merge
                     (db/get-db)
                     c
                     (friend/identity req)
                     {:is-admin? (in? roles "admin")})))))

(defn process-uploaded-file [album f]
  (let [filename (:filename f)
        exif (read-exif (:tempfile f))
        photo {:filename filename
               :captured (:captured exif)
               :exif exif
               :albums (if album [album] [])
               :caption nil}
        uploaded-file (io/file (str (db/get-uploads-path) filename))]
    (io/copy (:tempfile f) uploaded-file)
    (db/add-photo-to-db photo)
    (db/add-album-to-db {:name album :cover nil})
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
      (db/add-album-to-db {:name album :cover nil})
      (resp/redirect (str (:context req) redir))))

  (GET "/albums" req
    (render req "albums.html"))

  (GET "/albums/:album" req
    (let [album-name (get-in req [:params :album])
          album      (@db/albums (keyword album-name))
          all-images (map db/add-thumbs-to-photo (vals @db/images))
          full       (db/add-images-to-album all-images album)]
      (render req "album.html" {:album full})))

  (route/files "/thumbs" {:root (db/get-thumbs-path)})
  (route/resources "/")
  (route/not-found "Not Found"))

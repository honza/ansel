(ns ansel.views
  (:require [ansel.db :as db]
            [ansel.exif :refer [read-exif]]
            [ansel.resize :as r]
            [ansel.util :refer [in? paginate pretty-json safe-subvec slugify]]
            [clojure.java.io :as io]
            [jordan.core :refer :all]
            [ring.util.response :as resp]
            [selmer.parser :refer [render-file]]))

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
         user {:user (get-in req [:session :user])}
         gallery-name (db/get-gallery-name)]
      (render-file t (merge db
                            (dissoc ctx :db)
                            user
                            {:name gallery-name})))))

(defn make-image [filename exif album]
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
    (db/add-image-to-db (make-image filename exif album))
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

;; Handlers

(defn index-handler [req]
  (render req "index.html"))

(defn login-handler [req]
  (render req "login.html"))

(defn login-handler-post [req]
  (handle-login req))

(defn logout-handler [req]
  (-> (resp/redirect "/")
      (assoc :session {})))

(defn signup-handler [req]
  (render req "signup.html" ))

(defn signup-handler-post [req]
  nil
  )
;; (defn signup-handler-post [req]
;;   (let [{:keys [username password confirm] :as params} :params :as req]
;;     (if (and (not-any? s/blank? [username password confirm])
;;               (= password confirm))
;;       (let [user (select-keys params [:username :password])]
;;         (db/add-user-to-db user)
;;         (resp/redirect "/"))
;;       (assoc (resp/redirect (str (:context req) "/"))
;;              :flash "passwords don't match!"))))

(defn upload-handler [req]
  (with-admin-required
    (render req "upload.html")))

(defn upload-handler-post [req]
  (with-admin-required
    (let [uploaded         (get-in req [:params :files])
          album            (get-in req [:params :album])
          process-uploaded (partial process-uploaded-file album)]
      {:status 200
        :headers {"Content-Type" "application/json"}
        :body (pretty-json
                {:files (map process-uploaded uploaded)})})))

(defn organize-handler [req]
  (with-admin-required
    (render req "organize.html")))

(defn image-handler [req]
  (let [image-name (get-in req [:params :image])
        image      (get @db/images (keyword image-name))
        user       (get-in req [:session :user])
        comments   (db/get-comments-for-image (keyword image-name))
        like-text  (db/get-like-text (:likes image) (:username user))
        you-like   (when user
                      (in? (:likes image) (:username user)))]
    (if image
      (render req "single.html" {:image (db/add-thumbs-to-image image)
                                  :comments comments
                                  :like-text like-text
                                  :you-like you-like})
      (default-error req))))

(defn image-handler-post [req]
  (with-login-required
    (let [image-name (get-in req [:params :image])
          image (@db/images (keyword image-name))
          username (get-in req [:session :user :username])
          c (get-in req [:params :comment])]
      (db/comment-on-image image username c)
      (resp/redirect (str (:context req) "/image/" image-name)))))

(defn like-handler-post [req]
  (with-login-required
    (let [image-name (get-in req [:params :image])
          image      (@db/images (keyword image-name))
          username   (get-in req [:session :user :username])]
      (when-not (in? (:likes image) username)
        (db/add-image-to-db
          (update-in image [:likes] conj username)))
      (resp/redirect (str (:context req) "/image/" image-name)))))

(defn new-album-handler [req]
  (render req "album-form.html" {:next (or (get-in req [:params :next])
                                            "/albums")}))

(defn new-album-handler-post [req]
  (with-admin-required
    (let [album (get-in req [:params :album])
          redir (get-in req [:params :next])]
      (db/add-album-to-db {:name album :cover nil :slug (slugify album)})
      (resp/redirect (str (:context req) redir)))))

(defn albums-handler [req]
  (render req "albums.html"))


(defn album-handler [req]
  (let [album-name (get-in req [:params :album])
        album      (@db/albums (keyword album-name))
        all-images (map db/add-thumbs-to-image (vals @db/images))
        full       (db/add-images-to-album all-images album)]
    (render req "album.html" {:album full})))

(defn all-images-handler [req]
  (let [c (db/get-db)
        images (take page-size (:images c))]
    (render req "images.html" {:db c
                                :next (when (> (count (:images c)) page-size)
                                        2)
                                :images images})))

(defn all-images-page-handler [req]
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

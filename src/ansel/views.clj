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
            [ansel.util :refer :all]))

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
   (let [user {:user (get-in req [:session :user])}]
      (render-file t (merge ctx user)))))

(defn make-image [filename exif]
  (merge exif {:filename filename
               :caption nil}))

(defn process-uploaded-file [album user f]
  (let [filename (:filename f)
        exif (read-exif (:tempfile f))
        uploaded-file (io/file (str (db/get-uploads-path) filename))
        image (make-image filename exif)]
    (do
      (io/copy (:tempfile f) uploaded-file)
      (db/add-image-to-album
        (:id (db/add-image-to-db image (:id user)))
        (read-string album))
      {:name filename
       :url (r/thumb-url (r/resize-to-width* uploaded-file 900))
       :thumbnailUrl (r/make-small-thumb uploaded-file)})))

;; Routes ---------------------------------------------------------------------


(defn handle-login [req]
  (let [{{:keys [email password]} :params} req
        user (db/get-user (or email nil))]
    (if user
      (if (db/verify-bcrypt password (:password user))
        (-> (resp/redirect "/")
            (assoc :session {:user user}))
        (default-error req))
      (default-error req))))

(defroutes server-routes
  (GET "/" req (render req "index.html"
                       {:recent (db/get-images 5 true)
                        :years (keys (db/partition-by-year (db/get-images)))}))
  (GET "/login" req (render req "login.html"))
  (POST "/login" req (handle-login req))

  (GET "/logout" req (-> (resp/redirect "/")
                         (assoc :session {})))
  (GET "/signup" req (render req "signup.html" ))
  (POST "/signup" {{:keys [email password confirm] :as params} :params :as req}
        (if (and (not-any? s/blank? [email password confirm])
                 (= password confirm))
          (let [user (select-keys params [:email :password])]
            (db/add-user-to-db user)
            (resp/redirect "/"))
          (assoc (resp/redirect (str (:context req) "/")) :flash "passwords don't match!")))

  (GET "/upload" req
    (with-admin-required
      (render req "upload.html" {:albums (db/get-all-albums)})))

  (POST "/upload" req
    (with-admin-required
      (let [uploaded         (get-in req [:params :files])
            album            (get-in req [:params :album])
            user             (get-in req [:session :user])
            process-uploaded (partial process-uploaded-file album user)]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (pretty-json
                 {:files (map process-uploaded uploaded)})})))

  (GET "/organize" req
    (with-admin-required
      (render req "organize.html")))

  (GET "/image/:image" req
    (let [image-id  (read-string (get-in req [:params :image]))
          user      (get-in req [:session :user])
          image     (db/get-image-by-id image-id (:id user))
          comments  (db/get-comments-for-image (keyword image-id))
          you-like  (do-i-like? image)
          like-text (db/get-like-text (:num_likes image) you-like)]
      (if image
        (render req "single.html" {:image (db/add-thumbs-to-image image)
                                   :comments comments
                                   :like-text like-text
                                   :you-like you-like})
        (default-error req))))

  (POST "/image/:image" req
    (with-login-required
      (let [image-id (get-in req [:params :image])
            image (db/get-image-by-id image-id)
            email (get-in req [:session :user :email])
            c (get-in req [:params :comment])]
        (db/comment-on-image image email c)
        (resp/redirect (str (:context req) "/image/" image-id)))))

  (POST "/like" req
    (with-login-required
      (let [image-id (get-in req [:params :image])
            user     (get-in req [:session :user])
            image    (db/get-image-by-id image-id (:id user))]
        (when-not (do-i-like? image)
          (db/like-image image user))
        (resp/redirect (str (:context req) "/image/" image-id)))))

  (GET "/album" req
    (render req "album-form.html" {:next (or (get-in req [:params :next])
                                             "/albums")}))

  (POST "/album" req
    (with-admin-required
      (let [album (get-in req [:params :album])
            user  (get-in req [:session :user])
            redir (get-in req [:params :next])]
        (db/add-album-to-db
          {:name album :cover nil :slug (slugify album)}
          user)
        (resp/redirect (str (:context req) redir)))))

  (GET "/albums" req
    (let [albums (db/get-all-albums)]
      (render req "albums.html" {:albums albums})))

  (GET "/albums/:album" req
    (let [album-name (get-in req [:params :album])
          album      (db/get-album-by-slug album-name)
          all-images (db/get-images-for-album (:id album))
          full       (merge album {:images all-images})]
      (render req "album.html" {:album full})))

  (GET "/all" req
    (let [all-images (db/get-images)
          images (take page-size all-images)]
      (render req "images.html" {:next (when (> (count all-images)
                                                page-size)
                                         2)
                                 :images images})))

  (GET "/all/:page" req
    (let [images (vec (db/get-images))
          page (Integer. (get-in req [:params :page]))
          [start end] (paginate page page-size images)
          images (safe-subvec images start end)]
      (if images
        (render req "images.html" {:page page
                                   :next (inc page)
                                   :prev (dec page)
                                   :images images})
        (resp/redirect (str (:context req) "/all")))))

  (route/files "/thumbs" {:root (db/get-thumbs-path)})
  (route/resources "/")
  (route/not-found "Not Found"))

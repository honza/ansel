(ns ansel.server
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [info]]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as resp]
            [ring.middleware.session :refer [wrap-session]]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [selmer.parser :refer [render-file]]
            [ansel.db :as db]
            [ansel.resize :as r]
            [ansel.session :refer [filesystem-store]]
            [ansel.exif :refer [read-exif format-exif get-captured-timestamp]]
            [ansel.util :refer [pretty-json in?]]))

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
  ([req t]
   (render req t {}))
  ([req t c]
   (let [context (db/get-context)
         db      (prepare-db context)
         ident   (friend/identity req)]
    (render-file t (merge db c ident)))))

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
  (GET "/" req
       (println "session" (req :session))
       (render req "index.html"))
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
    (friend/authenticated (render req "upload.html")))

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
          image      (@db/images (keyword image-name))
          ident      (friend/identity req)]
      (render req "single.html"
              (merge
                {:image (add-thumbs-to-photo image)
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
    (render req "album-form.html"))

  (POST "/album" req
    (let [album (get-in req [:params :album])]
      (db/add-album-to-db album)
      (resp/redirect (str (:context req) "/upload"))))

  (GET "/albums" req
    (render req "albums.html"))

  (GET "/albums/:album" req
    (let [album-name (get-in req [:params :album])
          album      (@db/albums (keyword album-name))
          all-images (vals @db/images)
          images     (filter #(contains? (:albums %) (keyword album-name)) all-images)]
      (render req "album.html" {:album album
                                :images images})))

  (route/resources "/")
  (route/not-found "Not Found"))

(defn credential-fn [auth-map]
  (let [keyword-map (update-in auth-map [:username] keyword)]
    (creds/bcrypt-credential-fn @db/users keyword-map)))

(def server
  (-> server-routes
    (friend/authenticate {:allow-anon? true
                          :login-uri "/login"
                          :default-landing-url "/"
                          :credential-fn credential-fn
                          :workflows [(workflows/interactive-form)]})
    (handler/site {:session {:store (filesystem-store "session.json")}})))

(defn start-server []
  (run-jetty server {:port 8000 :join? false})
  (info "server online"))

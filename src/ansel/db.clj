(ns ansel.db
  (:require [taoensso.timbre :refer [info]]
            [cheshire.core :refer :all]
            [clojure.pprint :refer [pprint]]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clj-time.core :refer [now]]
            [clj-time.coerce :refer [to-timestamp]]
            [yesql.core :refer [defqueries]]
            [clojure.java.jdbc :refer [with-db-transaction]]
            [ansel.util :refer :all])
  (:import org.mindrot.jbcrypt.BCrypt))

(defqueries "sql/queries.sql")
(def config  (atom nil))
(def default-config {:upload-path nil
                     :thumb-path nil
                     :template-path nil
                     :database {:user "postgres"
                                :password nil
                                :host "//localhost:5432/ansel"}})

(defn load-config []
  (let [data (if (exists? "config.json")
               (parse-string (slurp "config.json") true)
               default-config)]
    (dosync
      (reset! config data)
      (info "data loaded from disk"))))

(defn assert-fs []
  (info "creating dirs")
  (fs/mkdirs (get-uploads-path))
  (fs/mkdirs (get-thumbs-path)))

(defn init []
  (load-config)
  (assert-fs))

;; This init is required when running the server via `lein ring server`
;; (init)

(defn get-db-spec []
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname (get-in @config [:database :host])
   :user (get-in @config [:database :user])})

(def tnow (comp to-timestamp now))

(defn hash-bcrypt [password]
  (BCrypt/hashpw password (BCrypt/gensalt)))

(defn verify-bcrypt [p h]
  (BCrypt/checkpw p h))

(defn get-user [email]
  (first (sql-get-user (get-db-spec) email)))

(defn sql-exists? [q]
  (-> q first :count pos?))

(defn user-exists? [db email]
  (sql-exists? (sql-user-exists db email)))

(defn users-exist? []
  (sql-exists? (sql-user-count (get-db-spec))))

(defn like-exists? [db image user]
  (sql-exists? (sql-like-exists db image user)))

(defn add-user-to-db [user & admin]
  (let [admin (if (and (seq admin) (first admin))
                true
                (if (users-exist?)
                  false
                  true))]
    (with-db-transaction [connection (get-db-spec)]
      (when-not (user-exists? connection (:email user))
        (sql-insert-user! connection
                          (hash-bcrypt (:password user))
                          (:email user)
                          (:name user)
                          admin)))))

(defn get-thumb-name
  "(get-thumb-name 'dog.jpg' 200)
  => 'dog_200.jpg'"
  [filename size]
  (let [[base ext] (fs/split-ext filename)]
    (str base "_" size ext)))

(defn add-thumbs-to-image [img]
  (let [small (get-thumb-name (:filename img) 200)
        big (get-thumb-name (:filename img) 900)]
    (assoc img :small-thumb small :big-thumb big)))

(defn add-thumb-urls [db]
  (let [thumbed (map add-thumbs-to-image (:images db))]
    (assoc db :images thumbed)))

(defn beef-up-image
  "Take the raw image value from the database and add various helpful
  values to it; e.g. a path to its thumbnail"
  [image]
  (->> image
       add-thumbs-to-image))

(defn get-images [& limit]
  (let [images (if (seq limit)
                 (sql-get-images-limit (get-db-spec) (first limit))
                 (sql-get-all-images (get-db-spec)))]
    (map beef-up-image images)))

(defn get-image-by-id 
  ([id] (get-image-by-id id 0))
  ([id user-id]
   (when-let [images (sql-get-image-by-id (get-db-spec) user-id id)]
     (beef-up-image (first images)))))

(defn add-image-to-db [image user-id]
  (sql-insert-image! (get-db-spec)
                     (:filename image)
                     (:caption image)
                     (:focal-length image)
                     (:focal-length-35 image)
                     (:shutter-speed image)
                     (:aperture image)
                     (:iso image)
                     (:exposure-compensation image)
                     user-id
                     (to-timestamp (:captured image))))

(defn add-image-to-album [image-id album-id]
  (sql-insert-image-to-album! (get-db-spec) image-id album-id))

(defn add-album-to-db [album user]
  (sql-insert-album!
    (get-db-spec)
    (:name album)
    (slugify (:name album))
    (:id user)))

(defn get-all-albums []
  (sql-get-all-albums (get-db-spec)))

(defn get-images-for-album [album-id]
  (sql-get-images-for-album (get-db-spec) album-id))

(defn get-album-by-slug [slug]
  (sql-get-album-by-slug (get-db-spec) slug))

(defn get-uploads-path []
  (or (:upload-path @config) "uploads/"))

(defn get-thumbs-path []
  (or (:thumb-path @config) "thumbs/"))

(defn get-template-path []
  (:template-path @config))

(defn like-image [image user]
  (with-db-transaction [connection (get-db-spec)]
    (when-not (like-exists? connection (:id image) (:id user))
      (sql-insert-like! (:id image) (:id user)))))

(defn get-like-text [num-likes you-like?]
  (if you-like?
    (case num-likes
      1 "You like this"
      2 "You and one other person likes this"
      (str "You and " (dec num-likes) " people like this"))
    (str num-likes " people like this")))

(defn comment-on-image [image user c]
  (sql-insert-comment! (get-db-spec)
                       (:id image)
                       (:id user)
                       c))

(defn get-comments-for-image [image]
  (sql-get-comments-for-image (get-db-spec) (:id image)))

(defn add-cover-to-album [images album]
  (assoc album :cover (or (:cover album)
                          (first (:images album)))))

(defn add-covers-to-albums [db]
  (let [images (:images db)
        albums (map (partial add-cover-to-album images) (:albums db))]
    (assoc db :albums albums)))

(defn add-images-to-album [images album]
  (let [images-in-album (filter
                          (fn [img]
                            (in? (:albums img) (:name album)))
                          images)]
    (assoc album :images images-in-album)))

(defn add-images-to-albums [db]
  (let [images (:images db)
        old-albums (:albums db)
        albums (map (partial add-images-to-album images) old-albums)]
    (assoc db :albums albums)))

(defn add-recent-images [db]
  (assoc db :recent (take 5 (:images db))))

(ns ansel.db
  (:require [taoensso.timbre :refer [info]]
            [cheshire.core :refer :all]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clj-time.core :refer [now]]
            [clj-time.format :refer [formatters unparse parse]]
            [ansel.util :refer [exists? minutes pretty-json cwd in?]])
  (:import org.mindrot.jbcrypt.BCrypt))

(def users (ref nil))
(def images (atom nil))
(def albums (atom nil))
(def likes (atom nil))
(def config (atom nil))
(def comments (atom nil))

(def running (atom true))
(def save-interval (minutes 3))
(def formatter (formatters :mysql))

(def default-db {:albums   {}
                 :images   {}
                 :users    {}
                 :likes    {}
                 :comments {}
                 :config   {:upload-path nil
                            :thumb-path nil
                            :template-path nil}})

;; User management ------------------------------------------------------------

(defn hash-bcrypt [password]
  (BCrypt/hashpw password (BCrypt/gensalt)))

(defn verify-bcrypt [p h]
  (BCrypt/checkpw p h))

(defn get-user [username]
  (get @users (keyword username)))

(defn user->entry [user]
  (let [{:keys [username password] :as user} user]
    {(keyword username)
     {:username username
      :password (hash-bcrypt password)}}))

(defn user-exists? [users username]
  (contains? users (keyword username)))

(defn add-user-to-db [user]
  (dosync
    (let [current-users (ensure users)]
      (if-not (user-exists? current-users (:username user))
        (alter users merge (user->entry user))))))

;; Photo management -----------------------------------------------------------

(defn add-photo-to-db [photo]
  (swap! images assoc (keyword (:filename photo)) photo)
  (info "photo added"))

(defn add-album-to-db [album]
  (swap! albums assoc (keyword (:slug album)) album)
  (info "album added"))

(defn get-uploads-path []
  (or (:upload-path @config) "uploads/"))

(defn get-thumbs-path []
  (or (:thumb-path @config) "thumbs/"))

(defn get-template-path []
  (:template-path @config))

(defn like-photo [photo user]
  (let [current-likes (or ((keyword (:filename photo)) @likes)
                          [])]
    (when-not (in? current-likes (:username user))
      (swap! likes assoc
             (keyword (:filename photo))
             (conj current-likes (:username user))))))

(defn time->string [t]
  (unparse formatter t))

(defn string->time [s]
  (parse formatter s))

(defn comment-on-photo [photo username c]
  (let [current-comments (or ((keyword (:filename photo)) @comments)
                             [])
        new-comment {:user username
                     :created (now)
                     :text c}]
    (swap! comments assoc
           (keyword (:filename photo))
           (conj current-comments new-comment))))

(defn get-comments-for-photo [photo]
  (get @comments photo))

;; Loading --------------------------------------------------------------------

(defn load-data-from-disk []
  (let [data (if (exists? "config.json")
               (parse-string (slurp "config.json") true)
               default-db)]
    (dosync
      (ref-set users    (:users data))
      (reset! images    (:images data))
      (reset! albums    (:albums data))
      (reset! likes     (:likes data))
      (reset! config    (:config data))
      (reset! comments  (:comments data))
      (info "data loaded from disk"))))

(defn get-context []
  {:users @users
   :images @images
   :albums @albums
   :likes @likes
   :config @config
   :comments @comments})

(defn save-data-to-disk []
  (info "saving data to disk")
  (spit "config.json" (pretty-json (get-context))))

(defn assert-fs []
  (info "creating dirs")
  (fs/mkdirs (get-uploads-path))
  (fs/mkdirs (get-thumbs-path)))

(defn get-thumb-name
  "(get-thumb-name 'dog.jpg' 200)
  => 'dog_200.jpg'"
  [filename size]
  (let [[base ext] (fs/split-ext filename)]
    (str base "_" size ext)))

(defn add-thumbs-to-photo [p]
  (let [small (get-thumb-name (:filename p) 200)
        big (get-thumb-name (:filename p) 900)]
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

(defn prepare-db [db]
  (-> db
      unroll-images
      unroll-albums
      sort-images
      sort-albums
      add-thumb-urls
      add-recent-images
      add-images-to-albums
      add-covers-to-albums))

(defn get-db []
  (prepare-db (get-context)))

(defn init []
  (load-data-from-disk)
  (assert-fs))

;; Background saving ----------------------------------------------------------

(defn start-saving []
  (let [t (Thread. (fn []
                     (while @running
                       (do
                         (Thread/sleep save-interval)
                         (save-data-to-disk)))))]

    (.start t)
    (info "background saving online")))

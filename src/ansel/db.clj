(ns ansel.db
  (:require [taoensso.timbre :refer [info]]
            [cheshire.core :refer :all]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clj-time.core :refer [now]]
            [clj-time.format :refer [formatters unparse parse]]
            [ansel.util :refer :all])
  (:import org.mindrot.jbcrypt.BCrypt))

(def users    (ref nil))
(def images   (atom nil))
(def albums   (atom nil))
(def likes    (atom nil))
(def config   (atom nil))
(def comments (atom nil))
(def running  (atom true))

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

(defn user->entry
  "If it's the first user, make them an admin"
  [user & admin]
  (let [{:keys [username password] :as user} user]
    {(keyword username)
     {:username username
      :password (hash-bcrypt password)
      :admin (or (first admin) false)}}))

(defn user-exists? [users username]
  (contains? users (keyword username)))

(defn add-user-to-db [user]
  (dosync
    (let [current-users (ensure users)
          user-count (count (keys current-users))
          first-user? (= 0 user-count)]
      (if-not (user-exists? current-users (:username user))
        (alter users merge (user->entry user first-user?))))))

;; Image management -----------------------------------------------------------

(defn add-image-to-db [image]
  (let [image (assoc image :created (now))]
    (swap! images assoc (keyword (:filename image)) image)
    (info "image added")))

(defn add-album-to-db [album]
  (let [album (assoc album :created (now))]
    (swap! albums assoc (keyword (:slug album)) album)
    (info "album added")))

(defn get-uploads-path []
  (or (:upload-path @config) "uploads/"))

(defn get-thumbs-path []
  (or (:thumb-path @config) "thumbs/"))

(defn get-template-path []
  (:template-path @config))

(defn like-image [image user]
  (let [current-likes (or ((keyword (:filename image)) @likes)
                          [])]
    (when-not (in? current-likes (:username user))
      (swap! likes assoc
             (keyword (:filename image))
             (conj current-likes (:username user))))))

(defn get-like-text [likes username]
  (if (in? likes username)
    (condp = (count likes)
      1 "You like this"
      2 "You and one other person likes this"
      (str "You and " (dec (count likes)) " people like this"))
    (str (count likes) " people like this")))

(defn time->string [t]
  (unparse formatter t))

(defn string->time [s]
  (parse formatter s))

(defn comment-on-image [image username c]
  (let [current-comments (or ((keyword (:filename image)) @comments)
                             [])
        new-comment {:user username
                     :created (now)
                     :text c}]
    (swap! comments assoc
           (keyword (:filename image))
           (conj current-comments new-comment))))

(defn get-comments-for-image [image]
  (get @comments image))

(defn map-time->string [m]
  (update-in m [:created] time->string))

(defn map-string->time [m]
  (update-in m [:created] string->time))

(defn stringify-times [coll]
  (val-map map-time->string coll))

(defn timify-strings [coll]
  (val-map map-string->time coll))

;; Loading --------------------------------------------------------------------

(defn load-data-from-disk []
  (let [data (if (exists? "config.json")
               (parse-string (slurp "config.json") true)
               default-db)]
    (dosync
      (ref-set users    (:users data))
      (reset! images    (timify-strings (:images data)))
      (reset! albums    (timify-strings (:albums data)))
      (reset! likes     (:likes data))
      (reset! config    (:config data))
      (reset! comments  (timify-strings (:comments data)))
      (info "data loaded from disk"))))

(defn get-context []
  {:users @users
   :images @images
   :albums @albums
   :likes @likes
   :config @config
   :comments @comments})

(defn save-data-to-disk []
  (let [context (get-context)
        context (update-keys
                  context
                  [:comments :images :albums]
                  stringify-times)]
    (info "saving data to disk")
    (spit "config.json" (pretty-json context))))

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

(defn add-thumbs-to-image [img]
  (let [small (get-thumb-name (:filename img) 200)
        big (get-thumb-name (:filename img) 900)]
    (assoc img :small-thumb small :big-thumb big)))

(defn add-thumb-urls [db]
  (let [thumbed (map add-thumbs-to-image (:images db))]
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

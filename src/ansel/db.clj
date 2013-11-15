(ns ansel.db
  (:require [taoensso.timbre :refer [info]]
            [cheshire.core :refer :all]
            [me.raynes.fs :as fs]
            [cemerick.friend.credentials :as creds]
            [clojure.java.io :as io]
            [ansel.util :refer [exists? minutes pretty-json cwd]]))

(def users (atom nil))
(def images (atom nil))
(def albums (atom nil))
(def likes (atom nil))
(def config (atom nil))
(def comments (atom nil))

(def running (atom true))
(def save-interval (minutes 3))

(def default-db {:albums {}
                 :images {}
                 :users {}
                 :likes {}
                 :config {:upload-path nil
                          :thumb-path nil
                          :template-path nil}
                 :comments {}})

;; User management ------------------------------------------------------------

(defn get-user [username]
  (get-in @users (keyword username)))

(defn user->entry [user]
  (let [{:keys [username password] :as user} user]
    {(keyword username)
     {:identity username
      :username username
      :password (creds/hash-bcrypt password)}}))

(defn user-exists? [users username]
  (contains? users (keyword username)))

(defn add-user-to-db [user]
  (dosync
    (let [current-users @users]
      (if-not (user-exists? current-users (:username user))
        (swap! users merge (user->entry user))))))

;; Photo management -----------------------------------------------------------

(defn add-photo-to-db [photo]
  (swap! images assoc (keyword (:filename photo)) photo)
  (info "photo added"))

(defn add-album-to-db [album]
  (swap! albums assoc (keyword album) {:name album})
  (info "album added"))

(defn get-uploads-path []
  (or (:upload-path @config) "uploads/"))

(defn get-thumbs-path []
  (or (:thumb-path @config) "thumbs/"))

(defn get-template-path []
  (:template-path @config))

;; Loading --------------------------------------------------------------------

(defn load-data-from-disk []
  (let [data (if (exists? "config.json")
               (parse-string (slurp "config.json") true)
               default-db)]
    (reset! users    (:users data))
    (reset! images   (:images data))
    (reset! albums   (:albums data))
    (reset! likes    (:likes data))
    (reset! config   (:config data))
    (reset! comments (:comments data))
    (info "data loaded from disk")))

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

(load-data-from-disk)
(assert-fs)

;; Background saving ----------------------------------------------------------

(defn start-saving []
  (let [t (Thread. (fn []
                     (while @running
                       (do
                         (Thread/sleep save-interval)
                         (save-data-to-disk)))))]

    (.start t)
    (info "background saving online")))

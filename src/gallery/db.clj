(ns gallery.db
  (:require [taoensso.timbre :refer [info]]
            [cheshire.core :refer :all]
            [cemerick.friend.credentials :as creds]
            [gallery.util :refer [exists? minutes pretty-json]]))

(def db (atom nil))
(def users (atom nil))
(def running (atom true))
(def save-interval (minutes 3))

(def default-db {:albums []
                 :images []
                 :users {}
                 :likes []
                 :config {:upload-path "uploads"
                          :template-path nil}
                 :comments []})

;; Loading --------------------------------------------------------------------

(defn load-data-from-disk []
  (let [data (if (exists? "config.json")
               (parse-string (slurp "config.json") true)
               default-db)]
    (reset! db (dissoc data :users))
    (reset! users (:users data))))

(defn save-data-to-disk []
  (info "saving data to disk")
  (spit "config.json"
        (pretty-json (merge @db {:users @users}))))

(load-data-from-disk)

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

;; Background saving ----------------------------------------------------------

(defn start-saving []
  (let [t (Thread. (fn []
                     (while @running
                       (do
                         (Thread/sleep save-interval)
                         (save-data-to-disk)))))]

    (.start t)
    (info "background saving online")))

(ns ansel.session
  (:import java.util.UUID)
  (:require [ring.middleware.session.store :refer :all]
            [ansel.util :refer [when-slurp]]))

(defn new-key []
  (str (UUID/randomUUID)))

(deftype FileSystemSessionStore [session-filename]
  SessionStore

  (read-session [s session-key]
    (let [session-text (when-slurp session-filename)
          session-data (read-string (or session-text "{}"))]
      (when session-data
        (session-data (keyword session-key)))))

  (write-session [_ session-key data]
    (let [k (or session-key (new-key))
          session-text (when-slurp session-filename)
          session-data (read-string (or session-text "{}"))
          updated-data (assoc session-data (keyword k) data)]
      (spit session-filename updated-data)
      k))

  (delete-session [_ session-key]
    (let [session-data (read-string (when-slurp session-filename))
          updated-data (dissoc session-data (keyword session-key))]
      (spit session-filename updated-data)
      nil)))

(defn filesystem-store [session-filename]
  (FileSystemSessionStore. session-filename))

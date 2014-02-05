(ns ansel.util
  (:require [clojure.java.io :refer [file]]
            [clojure.string :as string]
            [clojure.math.numeric-tower :refer [ceil]]
            [cheshire.core :refer :all]))

(defn exists? [path]
  (.exists (file path)))

(defn cwd []
  (.getCanonicalFile (file ".")))

(defn minutes [m]
  (* m 60 1000))

(defn pretty-json [m]
  (generate-string m {:pretty true}))

(defn in? [coll el]
  (some #(= el %) coll))

(defn when-slurp [filename]
  (when (exists? filename)
    (slurp filename)))

(defn dashify [s]
  (string/replace s #" " "-"))

(defn remove-specials [s]
  (string/replace s #"[\?\!\.\'\"]" ""))

(defn slugify [s]
  (-> s
      string/lower-case
      dashify
      remove-specials))

(defn safe-subvec
  "Safer subvec"
  [v start end]
  (when (or start end)
    (subvec v start end)))

(defn paginate [page page-size images]
  (when (pos? page)
    (let [page-count (int (ceil (/ (count images) page-size)))]
      (when (>= page-count page)
        (if (= page 1)
          [0 (min (dec page-size)
                  (count images))]
          [(* page-size (dec page))
           (min (dec (* page-size page))
                (count images))])))))

(defn val-map
  "Given a map where every val is a coll or a map, map f over each
  coll/map, preserving the map's structure"
  [f m]
  (into {} (map
             (fn [[k v]]
               [k (if (map? v)
                    (f v)
                    (vec (map f v)))])
             m)))

(defn update-keys [m ks f]
  (let [g (fn [c k]
            (update-in c [k] f))]
    (reduce g m ks)))

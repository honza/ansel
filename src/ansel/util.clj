(ns ansel.util
  (:require [clojure.java.io :refer [file]]
            [clojure.string :as string]
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

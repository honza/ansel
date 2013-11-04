(ns ansel.resize
  (:require [image-resizer.format :as f]
            [image-resizer.core :refer :all]
            [clojure.string :as s]
            [me.raynes.fs :as fs]
            [ansel.db :as db]
            [clojure.java.io :refer [file]]
            [taoensso.timbre.profiling :as profiling :refer (p profile)])
  (:import [java.io File]
           [javax.imageio ImageIO])
  (:gen-class))

(defn extension [path]
  (last (seq (s/split path #"\."))))

(defn as-file [buffered-file path]
  (ImageIO/write buffered-file (extension path) (File. path)))

(defn get-thumb-name [filename size]
  (let [[base ext] (fs/split-ext filename)]
    (str base "_" size ext)))

(defn make-thumb [filename size]
  (let [thumb-name (get-thumb-name filename size)
        thumb-full (str (db/get-thumbs-path) thumb-name)]
    (as-file (resize-to-width (file filename) size) thumb-full)
    thumb-name))

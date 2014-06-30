(ns ansel.resize
  (:require [image-resizer.format :as f]
            [image-resizer.core :refer :all]
            [image-resizer.util :refer [buffered-image]]
            [clojure.string :as s]
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

(defn process [src dest img-fns]
  (as-file
    ((apply comp img-fns) src)
    dest))

(defn flip [f]
  (fn [& args]
    (apply f (reverse args))))

(defn vertical? [f]
  (let [[w h] (dimensions (buffered-image f))]
    (> h w)))

(defn make-thumb
  "Resize image to height of size and then crop to width of size,
  making it a square"
  [f size]
  (let [thumb-name (db/get-thumb-name f size)
        thumb-full (str (db/get-thumbs-path) thumb-name)]
    (process f thumb-full
      (if (vertical? f)
        [(partial (flip crop-to-height) size)
         (partial (flip resize-to-width) size)]
        [(partial (flip crop-to-width) size)
         (partial (flip resize-to-height) size)]))
    thumb-name))

(defn resize-to-width* [f size]
  (let [thumb-name (db/get-thumb-name f size)
        thumb-full (str (db/get-thumbs-path) thumb-name)]
    (as-file (resize-to-width f size) thumb-full)
    thumb-name))

(defn thumb-url [filename]
  (str "/thumbs/" filename))

(defn make-small-thumb [f]
  (thumb-url (make-thumb f (:small-thumb-width @db/config))))

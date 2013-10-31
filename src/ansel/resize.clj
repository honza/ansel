(ns ansel.resize
  (:require [image-resizer.format :as f]
            [image-resizer.core :refer :all]
            [clojure.java.io :refer [file]]
            [taoensso.timbre.profiling :as profiling :refer (p profile)])
  (:gen-class))

(defn make-thumb [filename]
  (p :thumb (f/as-file (resize-to-width (file filename) 300)
                       filename)))

(defn get-images []
  (map str (rest (file-seq (file "images")))))

(get-images)

(defn make-all []
  (dorun
    (map make-thumb (get-images))))

(comment
(profile :info :thumbnail-profile
         (dotimes [n 100]
           (make-thumb "images/20131013_0001.jpg"))))

(comment
  (profile :info :multi-thumb (make-all)))

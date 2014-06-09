(ns ansel.exif
  (:require [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.format :as f])
  (:import [java.io File]
           [org.apache.sanselan ImageReadException Sanselan]))

(def exif-formatter (f/formatter (t/default-time-zone)
                                 "yyyy:MM:dd HH:mm:ss"
                                 "yyyy-MM-dd HH:mm:ss"))

(defn parse-exif-date [s]
  (c/to-date (f/parse exif-formatter s)))

(def interesting-exif-keywords
  (map keyword ["Focal Length" "Focal Length In 3 5mm Format" "Exposure Time"
   "Aperture Value" "ISO" "Exposure Compensation"]))

(defn parse-item [item]
  [(keyword (.getKeyword item))
   (.getText item)])

(defn get-captured-timestamp [exif]
  (let [value (exif (keyword "Create Date"))]
    (when value
      (parse-exif-date (s/replace value #"'" "")))))

(defn format-exif [exif]
  {:focal-length          (exif (keyword "Focal Length"))
   :focal-length-35       (exif (keyword "Focal Length In 3 5mm Format"))
   :shutter-speed         (exif (keyword "Exposure Time"))
   :aperture              (exif (keyword "FNumber"))
   :iso                   (exif (keyword "ISO"))
   :exposure-compensation (exif (keyword "Exposure Compensation"))
   :captured              (get-captured-timestamp exif)})

(defn read-exif [file]
  (let [metadata (Sanselan/getMetadata file)]
    (when metadata
      (format-exif
        (apply assoc {} (mapcat parse-item (.getItems metadata)))))))


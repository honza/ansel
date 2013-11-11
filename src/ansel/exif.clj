(ns ansel.exif
  (:require [clojure.string :as s])
  (:import [java.io File]
           [org.apache.sanselan ImageReadException Sanselan]))

(def interesting-exif-keywords
  (map keyword ["Focal Length" "Focal Length In 3 5mm Format" "Exposure Time"
   "Aperture Value" "ISO" "Exposure Compensation"]))

(defn parse-item [item]
  [(keyword (.getKeyword item))
   (.getText item)])

(defn read-exif [file]
  (let [metadata (Sanselan/getMetadata file)
        exif-items (.getItems metadata)]
    (apply assoc {} (mapcat parse-item exif-items))))

(defn get-captured-timestamp [exif]
  (s/replace (exif (keyword "Create Date")) #"'" ""))

(defn format-exif [exif]
  {:focal-length          (exif (keyword "Focal Length"))
   :focal-length-35       (exif (keyword "Focal Length In 3 5mm Format"))
   :shutter-speed         (exif (keyword "Exposure Time"))
   :aperture              (exif (keyword "FNumber"))
   :iso                   (exif (keyword "ISO"))
   :exposure-compensation (exif (keyword "Exposure Compensation"))
   :captured              (get-captured-timestamp exif)})

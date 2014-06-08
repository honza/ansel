(ns ansel.exif
  (:require [clojure.string :as s]
            [ansel.util :as util])
  (:import [java.io File]
           [org.apache.sanselan ImageReadException Sanselan]))

(def interesting-exif-keywords
  (map keyword ["Focal Length" "Focal Length In 3 5mm Format" "Exposure Time"
   "Aperture Value" "ISO" "Exposure Compensation"]))

(defn parse-item [item]
  [(keyword (.getKeyword item))
   (.getText item)])

(defn get-captured-timestamp [exif]
  (let [value (exif (keyword "Create Date"))]
    (when value
      (try
        (util/string->time (s/replace value "'" ""))
        (catch Exception e
          (util/string->time2 (s/replace value "'" "")))))))


(defn format-exif [exif]
  {:focal-length          (read-string (exif (keyword "Focal Length")))
   :focal-length-35       (read-string (exif (keyword "Focal Length In 3 5mm Format")))
   :shutter-speed         (exif (keyword "Exposure Time"))
   :aperture              (exif (keyword "FNumber"))
   :iso                   (read-string (exif (keyword "ISO")))
   :exposure-compensation (exif (keyword "Exposure Compensation"))
   :captured              (get-captured-timestamp exif)})

(defn read-exif [file]
  (let [metadata (Sanselan/getMetadata file)]
    (when metadata
      (format-exif
        (apply assoc {} (mapcat parse-item (.getItems metadata)))))))

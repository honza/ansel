(ns ansel.exif
  (:import [java.io File]
           [org.apache.sanselan ImageReadException Sanselan]))

(defn parse-item [item]
  [(keyword (.getKeyword item))
   (.getText item)])
 
(defn read-exif [file]
  (let [metadata (Sanselan/getMetadata file)
        exif-items (.getItems metadata)]
    (apply assoc {} (mapcat parse-item exif-items))))

(defn get-captured-timestamp [exif]
  (exif (keyword "Create Date")))
 
(comment
  (let [file (File. "resources/public/uploads/20131031_0012.jpg")]
    (println
      (get-captured-timestamp
      (read-exif file)))))

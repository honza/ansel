(ns ansel.urls
  (:require [ansel.db :as db]
            [ansel.views :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]))


(defroutes server-routes
  (GET  "/"              req (index-handler req))
  (GET  "/login"         req (login-handler req))
  (POST "/login"         req (login-handler-post req))
  (GET  "/logout"        req (logout-handler req))
  (GET  "/signup"        req (signup-handler req))
  (POST "/signup"        req (signup-handler-post req))
  (GET  "/upload"        req (upload-handler req))
  (POST "/upload"        req (upload-handler-post req))
  (GET  "/organize"      req (organize-handler req))
  (GET  "/image/:image"  req (image-handler req))
  (POST "/image/:image"  req (image-handler-post req))
  (POST "/like"          req (like-handler-post req))
  (GET  "/album"         req (new-album-handler req))
  (POST "/album"         req (new-album-handler-post req))
  (GET  "/albums"        req (albums-handler req))
  (GET  "/albums/:album" req (album-handler req))
  (GET  "/all"           req (all-images-handler req))
  (GET  "/all/:page"     req (all-images-page-handler req))

  (route/files "/thumbs" {:root (db/get-thumbs-path)})
  (route/resources "/")
  (route/not-found "Not Found"))

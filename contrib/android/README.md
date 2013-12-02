Android app
===========

The purpose of the Android app is allow the user to share images from their
device's gallery into Ansel.  The app should do this via an Android Intent.

Workflow
--------

1.  Authenticate with the app (app stores credentials locally)
2.  Show images in local gallery
3.  Allow user to select one or more images
4.  Ask user what album if any the selected images should go into
5.  Upload images to Ansel

REST API endpoints
------------------

```
/api/albums - GET  - get a JSON array of available albums
/api/images - POST - upload one of more images
```

REST API authentication
-----------------------

Basic auth headers.

1.  Build a string of `username:password`
2.  Base64 encode it
3.  Supply the encoded string in the `Authorization` header.

```
curl -X GET -H "Authorization: Basic <encoded string here>" http://....
```

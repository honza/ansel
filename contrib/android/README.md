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

There are two options:

1.  Basic auth
2.  Simplified oauth

Basic auth would be fine if all of the network communication was done over SSL.
Since we can't exactly guarantee that this will happen, I think we have a moral
responsibility to implement a more secure version of auth.

I think we can use the username and password as key and secret and HMAC sign
each request (including a timestamp and all that jazz).

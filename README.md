gallery
=======

Requirements
------------

* automatic thumbnail generation
* cropping of thumbnails
* albums
* logged in users can comment and like
* email subscription to updates
* captions
* JSON import/export
* backed by in-memory, periodically saved to disk JSON hash
* sane handling of image files
* image upload
* super simple deployment
* android app to provide intent
* bootstrap for design

Urls
----

    /album/rome
    /image/2013...
    /

Data structure
--------------

```yaml

albums:
    -
        name: Rome
        images:
            - 2013...jpg
images:
    -
        filename: 2013...jpg
        caption: Cool picture
        taken: 2013-10-04 13:01:21
        albums:
            - Rome

likes:
    -
        user: honza
        image: 2013...jpg
        created: 2013-10-04 13:01:21

comments:
    -
        user: honza
        image: 2013...jpg
        created: 2013-10-04 13:01:21
```

ns layout
---------

    gallery.resize
    gallery.db
    gallery.auth
    gallery.server
    gallery.core
        start server
        start background saving

License
-------

BSD, short and sweet

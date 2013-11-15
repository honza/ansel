ansel
=======

Ansel is a self-hosted, zero-configuration photo gallery application.  With
Ansel, you can showcase your photographs online without having to worry about
who owns your work and how it can be used by third parties.

Ansel is written almost entirely in Clojure and is distributed as an uberjar.
All you need to do is download the file and run it.  Ansel handles resizing,
exif data collection, captions, albums and much more.  All of your data is
internally stored as JSON and can be easily used by other applications.  Ansel
comes with a default set of templates and stylesheets and can be easily
extended customized.

Users can also create accounts to post comments and likes.  This feature is
intended for family photo galleries.

What already works
------------------

* Image upload
* Exif data collection
* User creation and authentication
* Album creation
* Thumbnails
* Likes
* Custom templates

Planned
-------

* Logged in users can comment
* Email subscription to updates
* Captions
* JSON import/export
* Android app to provide intent
* Sharing to Facebook

Developing
----------

Make sure that you have [Leiningen](https://github.com/technomancy/leiningen)
installed.

Clone, the repository:

    $ git clone git@github.com:honza/ansel.git

Run the server with code reloading:

    $ lein ring server

Note that running the server this way disables the background saving.

Releases
--------

You can download prebuilt jar files from the
[release page](http://honza.ca/ansel/).

License
-------

BSD, short and sweet

Acknowledgements
----------------

Thanks to @gotoplanb for the name

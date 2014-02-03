ansel
=======

Ansel is a self-hosted, zero-configuration image gallery application.  With
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

Demo
----

You can see a demo [here](http://ansel.demo.honza.ca/).

What already works
------------------

* Image upload
* Exif data collection
* User creation and authentication
* Album creation
* Thumbnails
* Likes
* Custom templates
* Logged in users can comment

Planned
-------

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

Making an uberjar
-----------------

This is easy with leiningen:

    $ lein uberjar

License
-------

BSD, short and sweet

Contributions
-------------

All contributions are welcome and appreciated.  Feel free to open an issue if
you have a question.

Acknowledgements
----------------

Thanks to @gotoplanb for the name

Changelog
---------

### 0.1.2 - (2013-11-21)

* Don't run init code when ns is loaded
* Redirect properly after album form submission
* Add albums page
* First cli arg is now a port number
* Add album covers
* Upload requires admin authorization
* Fix session key deletion
* Add more things to the template context

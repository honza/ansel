Developing
==========

Make sure that you have `Leiningen`_.
installed.

Clone, the repository:

::

    $ git clone git@github.com:honza/ansel.git

Run the server with code reloading:

::

    $ lein ring server

Note that running the server this way disables the background saving.

Run the server including background saving.  This is the same as running the
uberjar:

::

    $ lein run


.. _Leiningen: https://github.com/technomancy/leiningen

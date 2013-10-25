(defproject gallery "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [com.taoensso/timbre "2.6.2"]
                 [ring/ring-core "1.2.0"]
                 [ring/ring-jetty-adapter "1.2.0"]
                 [com.cemerick/friend "0.2.0"]
                 [selmer "0.4.7"]
                 [cheshire "5.2.0"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [image-resizer "0.1.6"]]
  :jvm-opts ["-Xmx1g" "-Djava.awt.headless=true"]
  :ring {:handler gallery.server/server}
  :plugins [[lein-ring "0.8.2"]]
  :main gallery.core)

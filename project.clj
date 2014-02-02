(defproject ansel "0.2.0"
  :description "Ansel, a self-hosted photo gallery"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/math.numeric-tower "0.0.3"]
                 [compojure "1.1.6"]
                 [me.raynes/fs "1.4.5"]
                 [com.taoensso/timbre "2.7.1"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [selmer "0.5.7"]
                 [cheshire "5.3.0"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [org.apache.sanselan/sanselan "0.97-incubator"]
                 [image-resizer "0.1.6"]
                 [clj-time "0.6.0"]
                 [jordan "0.1.1"]]
  :jvm-opts ["-Xmx1g" "-Djava.awt.headless=true"]
  :ring {:handler ansel.server/server}
  :plugins [[lein-ring "0.8.2"]]
  :main ^:skip-aot ansel.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

(defproject tidal-clojure "0.1.0"
  :description "CLI client for Tidal music streaming service"
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [clj-http "3.13.1"]
                 [cheshire "6.0.0"]]
  :main tidal-clojure.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

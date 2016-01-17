(defproject cljsbuild-example-simple "1.1.2"
  :description "A simple example of how to use lein-cljsbuild"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [cljs-ajax "0.5.3"]
                 [reagent "0.6.0-alpha"]]
  :plugins [[lein-cljsbuild "1.1.2"]]
  :cljsbuild {
              :builds [{:source-paths ["src-cljs"]
                        :compiler {:output-to "resources/public/js/main.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]})

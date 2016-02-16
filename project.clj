(defproject web-test-monitor "1.1.2"
  :description "GUI for fdc test server"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [figwheel-sidecar "0.5.0-1"]
                 [re-frame "0.6.0"]
                 [re-com "0.7.0"]
                 [com.keminglabs/c2 "0.2.3"]
                 [cljsjs/d3 "3.5.7-1"]
                 [cljsjs/plottable "1.12.0-0"]
                 [secretary "1.2.3"]
                 [prismatic/schema "1.0.5"]
                 [garden "1.3.0"]
                 [cljs-ajax "0.5.3"]
                 [reagent "0.5.1"]]
  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-figwheel "0.5.0-2"]
            [lein-garden "0.2.6"]
            [lein-doo "0.1.6"]]

  :clean-targets ^{:protect false} ["resources/public/js"
                                    "target"
                                    "test/js"
                                    "resources/public/css/compiled"]

  :figwheel {:css-dirs ["resources/public/css"]}

  :garden {:builds [{:id "screen"
                     :source-paths ["css"]
                     :stylesheet webtm.css/screen
                     :compiler {:output-to "resources/public/css/compiled/screen.css"
                                :pretty-print? true}}]}
  :doo {:build "test"}
  :cljsbuild {
              :builds [{:id "dev"
                        :source-paths ["src-cljs"]
                        :figwheel {:on-jsload "webtm.core/mount-root"}
                        :compiler {:main webtm.core
                                   :output-to "resources/public/js/main.js"
                                   :output-dir "resources/public/js/out"
                                   :asset-path "js/out"
                                   :pretty-print true
                                   :source-map-timestamp true}}
                       {:id "test"
                        :source-paths ["src-cljs" "test-cljs"]
                        :compiler {:output-to "resources/public/js/main.js"
                                   :main webtm.runner
                                   :optimizations :none}}

                       {:id "min"
                        :source-paths ["src-cljs"]
                        :compiler {:main webtm.core
                                   :output-to "resources/public/js/main.js"
                                   :optimizations :whitespace
                                   :closure-defines {goog.DEBUG false}
                                   :pretty-print false}}]})

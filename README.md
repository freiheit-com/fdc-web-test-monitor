# fdc-web-test-monitor

[![Build Status](https://travis-ci.org/freiheit-com/fdc-web-test-monitor.svg?branch=master)](https://travis-ci.org/freiheit-com/fdc-web-test-monitor)

web test monitoring ui

Build on re-frame, using garden re-com and plottable.

To develop: "lein garden auto & lein figwheel dev"

To test: "lein doo [phantom/slimer/chrome/...]"

To deploy: symlink into server, thon "lein clean && lein garden once && lein cljsbuild once min"

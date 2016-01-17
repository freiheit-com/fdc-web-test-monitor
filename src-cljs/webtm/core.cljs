(ns webtm.core
  (:require [reagent.core :as r])
  (:use [ajax.core :only [GET]]))

(enable-console-print!)

;TODO Read diff data from statistic server (to be implemented in server)

(defn- update-pos [pos]
  (let [new-pos (- pos 1)]
    (if (< new-pos 0) 100 new-pos)))

(defn ticker [text]
  (let [pos (r/atom 100)]
    (fn []
      (js/setTimeout #(swap! pos update-pos) 100)
      [:div {:style {:width "100%"}}
        [:span {:style {:left (str @pos "%") :position "absolute"}} text]])))

(defn ^:export run []
  (with-loaded-data
    (fn [data]
      (r/render [(partial ticker "+++ Project-H: +0.03%, Project-B: -0.08%, Project-E: +8,7% +++")]
                (js/document.getElementById "app")))))

(defn- with-loaded-data [f]
  (GET "https://localhost:8443/meta/projects" {:headers {"auth-token" "test" "Content-Type" "application/json"}
                                               :handler f
                                               :error-handler println}))

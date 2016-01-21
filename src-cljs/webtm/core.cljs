(ns webtm.core
  (:require [reagent.core :as r])
  (:use [ajax.core :only [GET]]))

(enable-console-print!)

;TODO Read diff data from statistic server (to be implemented in server)

(defn- update-pos [pos]
  (let [new-pos (- pos 1)]
    (if (< new-pos 0) 100 new-pos)))

(def ticker-text (r/atom ""))

(defn ticker []
  (let [pos (r/atom 100)]
    (fn []
      (js/setTimeout #(swap! pos update-pos) 150)
      [:div {:style {:width "100%" :overflow "hidden" :font-size "15pt"}}
        [:span {:style {:left (str @pos "%") :position "absolute" :width "100%"}} @ticker-text]])))


(defn- with-loaded-project-data [auth-token f]
  (GET "https://localhost:8443/meta/projects" {:headers {"auth-token" auth-token "Content-Type" "application/json"}
                                               :handler f
                                               :response-format :json
                                               :keywords? true
                                               :error-handler println}))

(defn- with-loaded-project-diff [auth-token f project]
  (GET (str "https://localhost:8443/statistics/coverage/diff/" project)
       {:headers {"auth-token" auth-token "Content-Type" "application/json"}
        :handler (partial f project)
        :response-format :json
        :keywords? true
        :error-handler println}))


(defn- append-project-diff [project diff]
  (swap! ticker-text (partial str (str "++ " project ": " (:diff-percentage diff) "% ++"))))

(defn ^:export run []
  (let [auth-token (js/prompt "Enter auth-token")]
    (r/render [ticker] (js/document.getElementById "app"))
    (with-loaded-project-data auth-token
      (fn [data] (dorun (map (comp (partial with-loaded-project-diff auth-token append-project-diff) :project) (:projects data)))))))

(ns webtm.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [webtm.handlers]
              [webtm.subs]
              [webtm.routes :as routes]
              [webtm.views :as views]
              [webtm.config :as config]))

(when config/debug?
  (println "dev mode"))

(defn mount-root []
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn init []
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (re-frame/dispatch [:connect-ws])
  (re-frame/dispatch [:fetch-meta])
  (mount-root))

(defn ^:export run []
  (let [meta (js/prompt "Enter meta auth-token")
        stats (js/prompt "Enter statistic auth-token")]
    (config/init meta stats)
    (init)))

(ns webtm.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/register-sub
 :active-panel
 (fn [db _]
   (reaction (:active-panel @db))))

(re-frame/register-sub
 :latest-params
 (fn [db]
   (reaction (:latest-params @db))))

(re-frame/register-sub
 :loading
 (fn [db]
   (reaction (:loading @db))))

(re-frame/register-sub
 :meta
 (fn [db]
   (reaction (:meta @db))))

(re-frame/register-sub
 :all
 (fn [db]
   (reaction (let [db-prj (:project @db)
                   graph-data (into [] (map (fn [a] (println (second a))[(first a) (second (second a))]) db-prj))
                   sorted (sort #(compare (first %1) (first %2)) graph-data)]
               sorted))))

(re-frame/register-sub
 :project-loaded
 (fn [db [_ name]]
   (reaction (get-in @db [:project name]))))

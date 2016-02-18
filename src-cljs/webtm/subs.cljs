(ns webtm.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [webtm.db :as db]
              [re-frame.core :as re-frame]
              [schema.core :as s :include-macros true]))

(def SingleCoverage [(s/one s/Str "name") (s/one db/MaybeCoverage "coverage")])
(def OverallData [SingleCoverage])
(def ProjectData {(s/eq "overall-coverage") {(s/eq "overall-coverage") SingleCoverage} :subprojects {s/Str {s/Str SingleCoverage}}})

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

(defn get-name-and-overall [prj]
  (let [subprojects (second prj)
        name (first prj)]
    [name (get-in subprojects ["overall-coverage" "overall-coverage"])]))

(re-frame/register-sub
 :overall
 (fn [db]
   (reaction (let [db-prj (:project @db)
                   graph-data (mapv get-name-and-overall db-prj)
                   sorted (sort-by first graph-data)]
               (s/validate OverallData sorted)
               sorted))))

(re-frame/register-sub
 :project-loaded
 (fn [db [_ name]]
   (reaction (let [db-prj (get-in @db [:project name])]
               ;; (println "prjs" db-prj)
               ;; (s/validate ProjectData db-prj)
               db-prj))))

(re-frame/register-sub
 :project-names
 (fn [db]
   (reaction (let [projectnames (keys (:project @db))]
               (sort projectnames)))))

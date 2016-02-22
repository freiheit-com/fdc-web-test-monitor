(ns webtm.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [webtm.db :as db]
              [re-frame.core :as re-frame]
              [taoensso.timbre :refer-macros [log spy]]
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

(defn- get-sort-fn [sort]
  (condp = sort
    "lines" (comp :lines second)
    "covered" (comp :covered second)
    "missed" #(let [cov (second %)] (- (:lines cov) (:covered cov)))
    "%" (comp :percentage second)
    (comp str first)))

(re-frame/register-sub
 :overall
 (fn [db [_ sort rev]]
   (reaction (let [db-prj (:project @db)
                   graph-data (mapv get-name-and-overall db-prj)
                   sorted (sort-by (get-sort-fn sort) graph-data)]
               (s/validate OverallData sorted)
               (if (= "true" rev) (reverse sorted) sorted)))))


(defn- get-prj-sort-fn [sort]
  (condp = sort
    "lines" (comp :lines second)
    "covered" (comp :covered second)
    "missed" #(let [cov (second %)] (- (:lines cov) (:covered cov)))
    "%" (comp :percentage second)
    (comp str first :subproject)))

(re-frame/register-sub
 :project-loaded
 (fn [db [_ name sort-param rev]]
   (reaction (let [subprojects (get-in @db [:project name :subproject])
                   overall-data (get-in @db [:project name "overall-coverage" "overall-coverage"])]
               (when (and overall-data subprojects)
                 (let [overall ["overall-coverage" overall-data]
                       sub-graph (for [[k v] subprojects] [k (get v "overall-coverage")])
                       graph-data (spy :error (into [overall] (sort-by first sub-graph)))
                       sorted (sort-by (get-prj-sort-fn sort-param) graph-data)
                       subproject-names (sort (map first subprojects))]
                   ;; (s/validate ProjectData db-prj)
                   [(if (= "true" rev) (reverse sorted) sorted) subproject-names]))))))

(re-frame/register-sub
 :project-history
 (fn [db [_ name]]
   (reaction (let [prj-hist (get-in @db [:project name :history])]
               (log :debug "hist" prj-hist)
               ;; (s/validate ProjectData db-prj)
               (sort-by first > prj-hist)))))

(re-frame/register-sub
 :project-names
 (fn [db]
   (reaction (let [projectnames (keys (:project @db))]
               (sort projectnames)))))

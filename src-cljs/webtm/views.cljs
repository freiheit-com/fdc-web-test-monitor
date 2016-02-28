(ns webtm.views
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [re-com.core :as re-com]
            [taoensso.timbre :refer-macros [log spy]]
            [cljsjs.plottable]
            [cljsjs.d3]
            [cljs-time.format :as tf]
            [webtm.routes :as routes]
            [secretary.core :as secretary]
            [clojure.string :as str]))

;; table stuff

(defn format-percentage [p]
  (str (.toFixed (* 100 p) 3) "%"))

(def interpolator (js/d3.interpolateLab "#ff0000" "#00ff00"))

(defn get-coverage-color [percent]
  (interpolator (* 2 (max 0 (- percent 0.5)))))

(defn coverage-line
  [[name data]]
  ^{:key name}
  [:tr
   [:td {:class :project-name} name]
   [:td (data :lines)]
   [:td (data :covered)]
   [:td (- (data :lines) (data :covered))]
   [:td {:class :project-percent}
    (let [p (data :percentage)]
      (if (nil? p) "?" (format-percentage p)))]])

(defn make-table-header [caption path params]
  [:th
   (let [reverse? (and (= "false" (:rev params)) (= (:sort params) caption))]
     [re-com/hyperlink-href
      :label [:span caption]
      :href (path {:query-params {:sort caption :rev reverse?}})])])

(defn coverage-table [data path params]
  (if-not data
    [:div "no data"]
    [:table {:class "table coverage"}
     [:thead
      [:tr
       (make-table-header "name" path params)
       (make-table-header "lines" path params)
       (make-table-header "covered" path params)
       (make-table-header "missed" path params)
       (make-table-header "%" path params)]]
     [:tbody
      (map coverage-line data)]]))

;; history table

(defn history-cell [data key]
  (let [p (get data :percentage)]
    ^{:key key}
    [:td {:class [:history-percent] :style {:background-color (get-coverage-color p)}}
     (if (nil? p) "?" (format-percentage p))]))

(defn history-line
  [[date data] subprojects]
  ^{:key date}
  [:tr
   [:td (tf/unparse (:date tf/formatters) date)]
   (history-cell (get-in data ["overall-coverage" "overall-coverage"]) (str "cell-" date "-overall"))
   (map #(history-cell (get-in data [:subproject % "overall-coverage"]) (str "cell-" date "-" %)) subprojects)])

(defn history-table [data subprojects]
  (if-not data
    [:div "no data"]
    [:table {:class "table history"}
     [:thead [:tr [:th] [:th "overall"] (for [sub subprojects] [:th {:key (str "prj-" sub)} sub])]]
     [:tbody (map #(history-line % subprojects) data)]]))


;; graph helper

(defn update-single-dataset [dataset data]
  (.data dataset data))


(defn update-component [comp chart dataset id update-dataset-fn]
  (let [data (clj->js (:data (reagent/props comp)))]
    (update-dataset-fn dataset data))
  (.renderTo chart (str "svg#" id)))

(defn graph-wrapper [data create-fn update-fn id]
  (let [{:keys [chart dataset]} (create-fn (:data data))]
    (reagent/create-class
     {:component-did-mount #(update-component % chart dataset id update-fn)
      :component-did-update #(update-component % chart dataset id update-fn)
      :display-name "chart"
      :reagent-render
      (fn [_] [:div {:class "chart"} [:svg {:id id}]])})))

;; overview graph

(defn make-overview-graph [data]
  (let [xScale (js/Plottable.Scales.Category.)
        yScale (doto (js/Plottable.Scales.Linear.)
                 (.domainMax 100))
        colorScale (doto (Plottable.Scales.InterpolatedColor.)
                     (.range (clj->js ["#ff0000", "#ff0000", "#00ff00"]))
                     (.domain (clj->js [0, 100])))
        xAxis  (js/Plottable.Axes.Category. xScale "bottom")
        yAxis  (js/Plottable.Axes.Numeric. yScale "left")
        pdata  (js/Plottable.Dataset. (clj->js data))
        plot   (doto (js/Plottable.Plots.Bar.)
                 (.x (fn [prj] (aget prj 0)) xScale)
                 (.y (fn [prj] (* 100 (.-percentage (aget prj 1)))) yScale)
                 (.attr "fill" (fn [prj] (* 100 (.-percentage (aget prj 1)))) colorScale)
                 (.addDataset pdata))
        chart  (js/Plottable.Components.Table. (clj->js [[yAxis plot] [nil xAxis]]))]
    {:chart chart :dataset pdata}))

(defn overview-graph [{:keys [data subprojects] :as props}]
  [graph-wrapper props #(make-overview-graph data) update-single-dataset "overview-chart"])

(defn make-scatter-line [percent xScale yScale percScale]
  (doto (js/Plottable.Plots.Line.)
    (.addDataset (js/Plottable.Dataset. (clj->js [[0 percent] [400000 percent]])))
    (.x #(aget % 0) xScale)
    (.y #(aget % 1) yScale)
    (.attr "stroke" percent percScale)
    (.attr "opacity" 0.5)))


(defn make-scatter-graph [data subprojects]
  (let [xScale (doto (js/Plottable.Scales.ModifiedLog.)
                 (.domainMin 100))
        yScale (doto (js/Plottable.Scales.Linear.)
                 (.domainMax 100))
        colorScale (doto (Plottable.Scales.Color.)
                     (.domain (clj->js subprojects)))
        percScale (doto (Plottable.Scales.InterpolatedColor.)
                     (.range (clj->js ["#ff0000", "#ff0000", "#00ff00"]))
                     (.domain (clj->js [0, 100])))
        xAxis  (js/Plottable.Axes.Numeric. xScale "bottom")
        yAxis  (js/Plottable.Axes.Numeric. yScale "left")
        legend (doto (js/Plottable.Components.Legend. colorScale)
                 (.maxEntriesPerRow 4))
        pdata  (js/Plottable.Dataset. (clj->js data))
        plot   (doto (js/Plottable.Plots.Scatter.)
                 (.x (fn [prj] (.-lines (aget prj 1))) xScale)
                 (.y (fn [prj] (* 100 (.-percentage (aget prj 1)))) yScale)
                 (.attr "fill" (fn [prj] (aget prj 0)) colorScale)
                 (.size 15)
                 (.attr "opacity" 1)
                 (.addDataset pdata))
        lines  (map #(make-scatter-line % xScale yScale percScale) [50 75 85])
        group  (Plottable.Components.Group. (clj->js (cons plot lines)))
        chart  (js/Plottable.Components.Table. (clj->js [[nil legend] [yAxis group] [nil xAxis]]))]
    {:chart chart :dataset {:dataset pdata :lines lines}}))

(defn update-scatter [dataset data]
  (update-single-dataset (:dataset dataset) data))


(defn scatter-graph [{:keys [data subprojects] :as props}]
  (let [subprojects-only (remove #(= "overall-coverage" (first %)) data)]
    [graph-wrapper
     {:data subprojects-only}
     #(make-scatter-graph subprojects-only subprojects)
     update-scatter
     "scatter-chart"]))


(defn get-stacked [prj i ds]
  (let [data (second (js->clj prj))
          covered (get data "covered")
          value (if (zero? (.metadata ds))
                  covered
                  (- (get data "lines") covered))]
    value))


(defn make-absolute-graph [data]
  (let [xScale (js/Plottable.Scales.Category.)
        yScale (doto (js/Plottable.Scales.Linear.))
        colorScale (doto (Plottable.Scales.InterpolatedColor.)
                     (.range (clj->js ["#00d000" "#f70000"])))
        xAxis  (js/Plottable.Axes.Category. xScale "bottom")
        yAxis  (js/Plottable.Axes.Numeric. yScale "left")
        pdata  (doto (js/Plottable.Dataset. (clj->js data))
                 (.metadata 0))
        pdata2  (doto (js/Plottable.Dataset. (clj->js data))
                 (.metadata 1))
        plot   (doto (js/Plottable.Plots.StackedBar.)
                 (.x (fn [prj] (aget prj 0)) xScale)
                 (.y get-stacked yScale)
                 (.attr "fill" (fn [prj i ds] (.metadata ds)) colorScale)
                 (.addDataset pdata)
                 (.addDataset pdata2))
        chart  (js/Plottable.Components.Table. (clj->js [[yAxis plot] [nil xAxis]]))]
    {:chart chart :dataset {:covered pdata :missing pdata2}}))

(defn update-absolute-datasets [{:keys [covered missing]} data]
  (.data missing data)
  (.data covered data))

(defn absolute-graph [{:keys [data subprojects]}]
  (let [subprojects-only (remove #(= "overall-coverage" (first %)) data)]
    [graph-wrapper
     {:data subprojects-only}
     #(make-absolute-graph subprojects-only)
     update-absolute-datasets
     "absolute-chart"]))

;; history graph

(defn get-y [data project-name]
  (-> data
      (js->clj)
      (second)
      (get-in ["subproject" project-name "overall-coverage" "percentage"])
      (* 100)))


(defn make-line [project-name xScale yScale colorScale pdata]
  (doto (js/Plottable.Plots.Line.)
    (.x (fn [prj] (aget prj 0)) xScale)
    (.y #(get-y % project-name) yScale)
    (.attr "stroke" project-name colorScale)
    (.addDataset pdata)))


(defn make-history-graph [data subprojects]
  (let [xScale (js/Plottable.Scales.Time.)
        yScale (doto (js/Plottable.Scales.Linear.)
                 (.domain #js [0 100]))
        colorScale (doto (Plottable.Scales.Color.)
                     (.domain (clj->js subprojects)))
        xAxis  (js/Plottable.Axes.Time. xScale "bottom")
        yAxis  (js/Plottable.Axes.Numeric. yScale "left")
        legend (doto (js/Plottable.Components.Legend. colorScale)
                 (.maxEntriesPerRow 4))
        pdata  (js/Plottable.Dataset. (clj->js data))
        plots  (map #(make-line % xScale yScale colorScale pdata) subprojects)
        group  (Plottable.Components.Group. (clj->js plots))
        chart  (js/Plottable.Components.Table. (clj->js [[nil legend] [yAxis group] [nil xAxis]]))]
    {:chart chart :dataset pdata}))

(defn history-graph [{:keys [data subprojects] :as props}]
  [graph-wrapper props #(make-history-graph data subprojects) update-single-dataset "historygraph"])

;; home

(defn overview-content [params]
  [(let [projects (re-frame/subscribe [:overall (:sort params) (:rev params)])]
    (fn []
      (let [data @projects
            project-names (map first data)
            props {:data data :subprojects project-names} ;; needs to be a map!
            ]
        (if (not-empty data)
          [:div
           [scatter-graph props]
           [overview-graph props]
           [absolute-graph props] ;;needs to be a map for react props!
           [:div {:class "panel panel-default data"}
            [coverage-table data routes/home (merge {:sort "name" :rev "false"} params)]]]
          [:div "no data"]))))])

(defn project-overview-panel
  [params]
  [:div {:class "panel panel-default overview"}
   [:div {:class "panel-heading"} [:h2 "Overview"]]
   [overview-content params]])

(defn home-panel [params]
  [re-com/v-box
   :class "home"
   :gap "1em"
   :children [[project-overview-panel params]]])

;;project

(defn project-coverage
  [name data params subprojects]
  [:div {:class "panel panel-default col-6-lg overview"}
   [:div {:class "panel-heading"} [:h2 name]]
   (if-not data
     "no data"
     [:div
      [scatter-graph {:data data :subprojects subprojects}]
      [overview-graph {:data data}] ;;needs to be a map for react props!
      [absolute-graph {:data data}] ;;needs to be a map for react props!
      [coverage-table data #(routes/project (merge {:name name} %)) (merge {:sort "name" :rev "false"} params)]])])

(defn project-history-panel
  [data subprojects]
  [:div {:class "panel panel-default col-6-lg overview"}
   [:div {:class "panel-heading"} [:h2 "History"]]
   (if-not data
     "no data"
     [:div
      [history-graph {:data data :subprojects subprojects}]
      [history-table data subprojects]])])

(defn project-history [project-name subprojects]
  [(let [history-data (re-frame/subscribe [:project-history project-name])]
    (fn []
      [project-history-panel @history-data subprojects]))])


(defn project-content  [project-name data params]
  (let [graph-data (first @data)
        subprojects (second @data)]
    [re-com/v-box
     :class "row"
     :gap "1em"
     :children [[project-coverage project-name graph-data params subprojects]
                [project-history project-name subprojects]]]))


(defn project-panel [params]
  [(let [project-name (:name params)
         data (re-frame/subscribe [:project-loaded project-name (:sort params) (:rev params)])]
     (fn []
       (re-frame/dispatch [:fetch-project-history project-name])
       [project-content project-name data params]))])

;; nav

(defn project-link [name classes]
  ^{:key name}
  [:li {:class classes}
   [re-com/hyperlink-href
    :label [:span name]
    :href (routes/project {:name name})]])

(defn make-nav-entry [name active]
  (let [classes ["nav" (when (= name active) "active")]
        class-str (str/join " " classes)]
    (project-link name class-str)))


(defn projects-nav [active]
  [(let [project-names (re-frame/subscribe [:project-names])]
    (fn []
      [:ul {:class "nav navbar-nav"}
       (map #(make-nav-entry % active) @project-names)]))])

(defn navbar [active]
  [:nav {:class "navbar navbar-default"}
   [:div {:class "container-fluid"}
    [:div {:class "navbar-brand"}
     [re-com/hyperlink-href
      :label [:div
              [:span {:class "glyphicon glyphicon-text-width"}]
              [:span {:class "glyphicon glyphicon-ok"}]]
      :href (routes/home)]]
    [:div {:class "navbar-header navbar-links"}
     [projects-nav active]]]])


;; main

(defmulti panels identity)
(defmethod panels :home-panel [_ params] [home-panel params])
(defmethod panels :project-panel [_ params] [project-panel params])
(defmethod panels :default [] [:div])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        latest-params (re-frame/subscribe [:latest-params])]
    (fn []
      [re-com/v-box
       :height "100%"
       :children [[navbar (:name @latest-params)]
                  [:div {:class "body container"}
                   [panels @active-panel @latest-params]]]])))

(ns webtm.views
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [re-com.core :as re-com]
            [taoensso.timbre :refer-macros [log spy]]
            [cljsjs.plottable]
            [cljsjs.d3]
            [webtm.routes :as routes]
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
   [:td (data :covered)]
   [:td (data :lines)]
   [:td {:class :project-percent}
    (let [p (data :percentage)]
      (if (nil? p) "?" (format-percentage p)))]])

(defn coverage-table [data]
  (if-not data
    [:div "no data"]
    [:table {:class "table coverage"}
     [:thead [:tr [:th "name"] [:th "covered"] [:th "lines"] [:th "%"]]]
     [:tbody
      (map coverage-line data)]]))

(defn history-cell [data key]
  (let [p (get data :percentage)]
    ^{:key key}
    [:td {:class [:history-percent] :style {:background-color (get-coverage-color p)}}
     (if (nil? p) "?" (format-percentage p))]))


(defn history-line
  [[date data] subprojects]
  ^{:key date}
  [:tr
   [:td date]
   (history-cell (get-in data ["overall-coverage" "overall-coverage"]) (str "cell-" date "-overall"))
   (map #(history-cell (get-in data [:subproject % "overall-coverage"]) (str "cell-" date "-" %)) subprojects)])

(defn history-table [data subprojects]
  (if-not data
    [:div "no data"]
    [:table {:class "table history"}
     [:thead [:tr [:th] [:th "overall"] (for [sub subprojects] [:th {:key (str "prj-" sub)} sub])]]
     [:tbody (map #(history-line % subprojects) data)]]))


;; global

(defn project-link
  ([name]
   (project-link name {}))
  ([name opts]
   ^{:key name}
   [:li opts
    [re-com/hyperlink-href
     :label [:span name]
     :href (routes/project {:name name})]]))

;; home

(defn loader
  [children]
  (let [loading (re-frame/subscribe [:loading])]
    (fn []
      (if @loading
        [:div "loading"]
        children))))

(defn make-graph [data]
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


(defn plotfn [comp chart dataset]
  (let [data (clj->js (:data (reagent/props comp)))]
    (.data dataset data))
  (.renderTo chart "svg#overview-chart"))

(defn overview-graph [data]
  (let [{:keys [chart dataset]} (make-graph (:data data))]
    (reagent/create-class
     {:component-did-mount #(plotfn % chart dataset)
      :component-did-update #(plotfn % chart dataset)
      :display-name "chart"
      :reagent-render
      (fn [_] [:div {:class "chart"} [:svg {:id "overview-chart"}]])})))

(defn overview-content []
  (let [projects (re-frame/subscribe [:overall])]
    (fn []
      (let [data @projects
            props {:data data}] ;; needs to be a map!
        (if (not-empty data)
          [:div
           [overview-graph props]
           [:div {:class "panel panel-default data"}
            [coverage-table data]]]
          [:div "no data"])))))


(defn project-overview-panel
  []
  [:div {:class "panel panel-default overview"}
   [:div {:class "panel-heading"} [:h2 "Overview"]]
   [overview-content]])

(defn home-panel []
  [re-com/v-box
   :class "home"
   :gap "1em"
   :children [[loader [project-overview-panel]]]])

;;project

(defn project-coverage
  [name data]
  [:div {:class "panel panel-default col-6-lg overview"}
   [:div {:class "panel-heading"} [:h2 name]]
   (if-not data
     "no data"
     [:div
      [overview-graph {:data data}] ;;needs to be a map for react props!
      [coverage-table data]])])

(defn project-history-panel
  [data subprojects]
  [:div {:class "panel panel-default col-6-lg overview"}
   [:div {:class "panel-heading"} [:h2 "History"]]
   (if-not data
     "no data"
     [:div
      [history-table data subprojects]])])

(defn project-history [project-name subprojects]
  [(let [history-data (re-frame/subscribe [:project-history project-name])]
    (fn []
      [project-history-panel @history-data subprojects]))])


(defn project-content  [project-name data]
  (let [overall-data (get-in @data ["overall-coverage" "overall-coverage"])
        overall (when overall-data ["overall-coverage" overall-data])
        subprojects (get @data :subproject)
        sub-graph (for [[k v] subprojects] [k (get v "overall-coverage")])
        graph-data (into [overall] (sort-by first sub-graph))]
    [re-com/v-box
     :class "row"
     :gap "1em"
     :children [[project-coverage project-name (when overall graph-data)]
                [project-history project-name (sort (map first subprojects))]]]))


(defn project-panel [param]
  [(let [project-name (:name param)
         data (re-frame/subscribe [:project-loaded project-name])]
     (fn []
       (re-frame/dispatch [:fetch-project-history project-name])
       [project-content project-name data]))])

;; nav

(defn projects-nav []
  (let [project-names (re-frame/subscribe [:project-names])]
    (fn []
      [:ul {:class "nav navbar-nav"}
       (map #(project-link % {:class "nav"}) @project-names)])))

(defn navbar []
  [:nav {:class "navbar navbar-default"}
   [:div {:class "container-fluid"}
    [:div {:class "navbar-brand"}
     [re-com/hyperlink-href
      :label [:div
              [:span {:class "glyphicon glyphicon-text-width"}]
              [:span {:class "glyphicon glyphicon-ok"}]]
      :href (routes/home)]
     ]
    [:div {:class "navbar-header navbar-links"}
     [projects-nav]]]])


;; main

(defmulti panels identity)
(defmethod panels :home-panel [] [home-panel])
(defmethod panels :project-panel [_ params] [project-panel params])
(defmethod panels :default [] [:div])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        latest-params (re-frame/subscribe [:latest-params])]
    (fn []
      [re-com/v-box
       :height "100%"
       :children [[navbar]
                  [:div {:class "body container"}
                   [panels @active-panel @latest-params]]]])))

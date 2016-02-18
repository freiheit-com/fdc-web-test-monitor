(ns webtm.views
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [re-com.core :as re-com]
            [cljsjs.plottable]
            [webtm.routes :as routes]))

;; graph stuff

(defn format-percentage [p]
  (str (.toFixed (* 100 p) 3) "%"))


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

(defn coverage-graph-bar [[name data]]
  (let [percent (* 100 (data :percentage))
        level (condp < percent
                95 "nice"
                85 "okay"
                75 "warning"
                "error")]
    [:div {:key name
      :class (str "column")}
     [:div
      {:class (str "bar " level)
       :style {:height (str percent "%")}}
      [:label name]
      [:span {:class "value"} percent]]]))


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
      (fn [data] [:div {:class "chart" :data data} [:svg {:id "overview-chart"}]])})))

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
      [overview-graph {:data data}] ;;needs to be a map!
      [coverage-table data]])])

(defn project-panel []
  (let [prj (re-frame/subscribe [:latest-params])]
    (fn []
      (let [data (re-frame/subscribe [:project-loaded (:name @prj)])
            project-name (:name @prj)]
        (fn []
        (println "render" @prj @data)
        (let [overall ["overall-coverage" (get-in @data ["overall-coverage" "overall-coverage"])]
              subprojects (get @data :subproject)
              sub-graph (for [[k v] subprojects] [k (get v "overall-coverage")])
              graph-data (into [overall] (sort-by first sub-graph))]
          [re-com/v-box
           :class "row"
           :gap "1em"
           :children [[project-coverage project-name graph-data]]]))))))

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
(defmethod panels :project-panel [] [project-panel])
(defmethod panels :default [] [:div])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        latest-params (re-frame/subscribe [:latest-params])]
    (fn []
      [re-com/v-box
       :height "100%"
       :children [
                  (navbar)
                  [:div {:class "body container"}
                   (panels @active-panel @latest-params)]]])))

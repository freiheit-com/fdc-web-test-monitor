(ns webtm.views
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [re-com.core :as re-com]
            [c2.scale :as scale]
            [cljsjs.plottable]
            [webtm.routes :as routes]))

;; graph stuff

(defn coverage-line
  [[name data]]
  ^{:key name}
  [:tr [:td {:class :project-name} name] [:td (data :covered)] [:td (data :lines)] [:td (* 100 (data :percentage))]])


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


(defn coverage-graph [data]
  [:div {:class "panel-body graph"}
   (if data
     (map coverage-graph-bar data)
     nil)])

;; global

(defn project-link
  ([project]
   (project-link project {}))
    ([project opts]
   (let [name (project :project)]
    ^{:key name}
     [:li opts
      [re-com/hyperlink-href
       :label [:span name]
       :href (routes/project {:name name})]])))

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
                     (.range (clj->js ["#ff0000", "#00ff00"])))
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
    (.error js/console data)
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
  (let [projects (re-frame/subscribe [:all])]
    (fn []
      (let [data @projects]
        (if (not-empty data)
          [:div
           [overview-graph {:data data}]
           [coverage-table data]]
          [:div "no data"])))))


(defn project-overview-panel
  []
  [:div {:class "panel panel-default col-6-lg overview"}
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
  [:div {:class "panel panel-default col-6-lg"}
   [:div {:class "panel-heading"} [:h2 name]]
   (if-not data
     "no data"
     [:div
      [coverage-graph data]
      [coverage-table data]])])

(defn project-panel [{:keys [name]}]
  (let [coverage-data (re-frame/subscribe [:project-loaded name])]
    (fn []
      [re-com/v-box
       :class "row"
       :gap "1em"
       :children [[project-coverage name @coverage-data]]])))

;; nav

(defn projects-nav []
  (let [data (re-frame/subscribe [:meta])]
    (fn []
      [:ul {:class "nav navbar-nav"}
       (map #(project-link % {:class "nav"}) @data)])))

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
       :children [
                  (navbar)
                  [:div {:class "body container"}
                   (panels @active-panel @latest-params)]]])))

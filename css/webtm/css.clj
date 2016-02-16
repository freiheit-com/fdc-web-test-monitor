(ns webtm.css
  (:require [garden.def :refer [defstyles]]
            [garden.units :refer [px percent]]
            [garden.color :refer :all]))

(defstyles screen
  [:.nav :.navbar-links {:line-height (px 30)}]
  [:.nav-brand :.glyphicon-ok {:color :lawngreen :left (px -10)}]
  [:.coverage {:text-align :right} [:thead [:th {:text-align :center}]][:.project-name {:text-align :left :font-weight :bold}]]
  [:.overview [:.chart {:height (px 300) :margin (px 30)}]]
  [[:.graph {:display :table
             :height (px 300)
             :width (percent 90)
             ;; :margin [[(px 50):auto]]
             :padding 0
             :border-left [[(px 1) :solid :lightgrey]]
             :border-bottom [[(px 1) :solid :darkgrey]]}]
   [:.column {:height (percent 100) :max-width (percent 50) :margin :auto :display :table-cell :vertical-align :bottom}]
   [:.bar {:position :relative :box-shadow [[(px 2) (px -2) (px 6) :darkgrey]]}]
   [:label {:position :absolute :left 0 :right 0 :top (percent 100) :text-align :center}]
   [:.value {:position :absolute
             :left 0
             :right 0
             :top (percent 33)
             :line-height (percent 100)
             :text-align :center
             :color :beige
             :font-size (px 20)
             :font-weight :bold}]
   [[:.error {:background-color :red}]
    [:.warning {:background-color :orange}]
    [:.okay {:background-color :green}]
    [:.nice {:background-color :lawngreen}]]])

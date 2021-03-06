(ns webtm.handlers
  (:require [re-frame.core :as re-frame :refer [register-handler dispatch]]
            [ajax.core :refer [GET POST]]
            [taoensso.timbre :refer-macros [log]]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.periodic :as periods]
            [webtm.config :refer [stats-token meta-token server-rest-url server-ws-url]]
            [webtm.db :as db]))

(def +time+ 10000)

(register-handler
 :initialize-db
 (fn  [_ _]
   db/default-db))

(register-handler
 :set-active-panel
 (fn [db [_ active-panel params]]
   (log :info "active-panel" active-panel "params" params)
   (assoc db :active-panel active-panel :latest-params params)))

(register-handler
 :active-project
 (fn [db [_ project]]
   (assoc db :active-project project)))

(defn project-names [db]
  (sort (map first (:project db))))

(defn dispatch-active [name]
  (dispatch [:active-project name])
  (dispatch [:fetch-meta])
  (dispatch [:set-active-panel :slide-panel {:name name}]))


(register-handler
 :auto
 (fn [db [_ auto]]
   (let [auto-on (= :on auto)]
     (if auto-on
       (do (js/setTimeout #(dispatch [:next-panel]) +time+)
           (dispatch-active (first (project-names db))))
       (dispatch [:set-active-panel :home-panel]))
     (assoc db :auto auto-on))))

(register-handler
 :next-panel
 (fn [db _]
   (let [active-prj-name (:name (:latest-params db))
         name-array (to-array (project-names db))
         idx (.indexOf name-array active-prj-name)
         next (nth (cycle name-array) (+ 1 idx))]
     (dispatch-active next)
     (if (:auto db) (js/setTimeout #(dispatch [:next-panel]) +time+)))
   db))

(register-handler
  :fetch-meta
  (fn
    [db _]
    (GET
     (server-rest-url "/meta/projects")
     {:headers       {"auth-token" (meta-token)}
      :handler       #(dispatch [:meta-updated %1])})
    (assoc db :loading? true)))

(register-handler
  :meta-updated
  (fn
    [db [_ response]]
    (js/setTimeout #(dispatch [:fetch-meta]) 300000)
    (let [projects (db/parse-meta response)]
      (doall (map #(dispatch [:fetch-project %]) (keys projects)))
      (when (:active-project db) (dispatch [:fetch-project-details (:active-project db)]))
      (-> db
          (assoc :meta projects)
          (assoc :loading? false)))))

(defn request-coverage [{:keys [project subproject language time] :as id}]
  (let [base-url [(server-rest-url "/statistics/coverage")]
        time-parts ["/" (or time "latest")]
        project-parts ["/" project]
        sub-parts (if subproject ["/" subproject] [])
        lang-parts (if language ["/" language] [])
        url-parts (concat base-url time-parts project-parts sub-parts lang-parts)
        url (apply str url-parts)]
    (GET
     url
     {:headers {"auth-token" (stats-token)}
      :handler #(dispatch (into [:project-updated id] %1))})))


(register-handler
  :fetch-project
  (fn
    [db [_ name]]
    (request-coverage {:project name})
    db))


(register-handler
  :fetch-project-details
  (fn
    [db [_ name time]]
    (request-coverage {:project name :time time})
    (let [project (get-in db [:meta name])
          subprojects (:subprojects project)
          sub-names (map :subproject subprojects)]
         (doall (map #(request-coverage {:project name :subproject % :time time}) sub-names)))
    db))


(register-handler
  :fetch-project-history
  (fn
    [db [_ name]]
    (let [time-range (take 7 (periods/periodic-seq (t/now) (t/days -1)))
          formatted-range (map #(tf/unparse (tf/formatters :date) %) time-range)]
      (log :debug "fetching for dates: " formatted-range)
      (doall (map #(dispatch [:fetch-project-details name %]) formatted-range))
      )
    db))

(register-handler
  :project-updated
  (fn
    [db [_ {:keys [project subproject language time] :as token} response]]
    (let [data (db/parse-project response)
          path (concat [:project project]
                       (if time [:history time])
                       (if subproject [:subproject subproject] ["overall-coverage"])
                       (when language [:language language]))
          db-path (into [] path)]
      (log :debug path)
      (assoc-in db db-path data))))

(register-handler
 :connect-ws
 (fn  [db _]
   (comment (let [ws (js/WebSocket. (server-ws-url))]
             (set! (.-onopen ws))
             (set! (.-onclose ws) #(println "close" %))
             (set! (.-onmessage ws) (fn [msg] (.send ws "baz")(.log js/console "msg" msg)))))
   (assoc db :ws-connected? false)))

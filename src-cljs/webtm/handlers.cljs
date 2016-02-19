(ns webtm.handlers
  (:require [re-frame.core :as re-frame :refer [register-handler dispatch]]
            [ajax.core :refer [GET POST]]
            [webtm.config :refer [stats-token meta-token server-rest-url server-ws-url]]
            [webtm.db :as db]))

(register-handler
 :initialize-db
 (fn  [_ _]
   db/default-db))

(register-handler
 :set-active-panel
 (fn [db [_ active-panel params]]
   (assoc db :active-panel active-panel :latest-params params)))

(register-handler
 :active-project
 (fn [db [_ project]]
   (assoc db :active-project project)))

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

(defn request-coverage [{:keys [project subproject language] :as id}]
  (let [base-url [(server-rest-url "/statistics/coverage/latest/") project]
        sub-parts (if subproject ["/" subproject] [])
        lang-parts (if language ["/" language] [])
        url-parts (concat base-url sub-parts lang-parts)
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
    [db [_ name]]
    (request-coverage {:project name})
    (let [project (get-in db [:meta name])
          subprojects (:subprojects project)
          sub-names (map :subproject subprojects)]
         (doall (map #(request-coverage {:project name :subproject %}) sub-names)))
    db))

(register-handler
  :project-updated
  (fn
    [db [_ {:keys [project subproject language] :as token} response]]
    (let [data (db/parse-project response)
          path (concat [:project project]
                       (if subproject [:subproject subproject] ["overall-coverage"])
                       (when language [:language language]))
          db-path (into [] path)]
      (assoc-in db db-path data))))

(register-handler
 :connect-ws
 (fn  [db _]
   (comment (let [ws (js/WebSocket. (server-ws-url))]
             (set! (.-onopen ws))
             (set! (.-onclose ws) #(println "close" %))
             (set! (.-onmessage ws) (fn [msg] (.send ws "baz")(.log js/console "msg" msg)))))
   (assoc db :ws-connected? false)))

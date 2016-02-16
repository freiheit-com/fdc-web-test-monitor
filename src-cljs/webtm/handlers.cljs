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
    (let [projects (db/parse-meta response)]
      (doall (map #(dispatch [:fetch-project (% :project)]) projects))
      (-> db
          (assoc :meta projects)
          (assoc :loading? false)))))

(defn request-coverage [{:keys [project subproject language] :as id}]
  (let [base-url [(server-rest-url "/statistics/coverage/latest/") project]
        sub-parts (if subproject ["/" subproject] [])
        lang-parts (if subproject ["/" language] [])
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
    ;; (let [projects (get-in db [:meta "projects"])
    ;;       grouped (group-by #(get % "project") projects)
    ;;       subprojects (keys grouped)]
    ;;   ;; (.error js/console (clj->js subprojects))
    ;;   (doall (map #(request-coverage {:project name :subproject %}) subprojects)))
    ;; (request-coverage {:project name})
    db))

(register-handler
  :project-updated
  (fn
    [db [_ token response]]
    ;; (.error js/console (clj->js token))
    (let [data (db/parse-project response)
          path (apply into [] token)]
      (assoc-in db path data))))

(register-handler
 :connect-ws
 (fn  [db _]
   (let [ws (js/WebSocket. (server-ws-url))]
     (set! (.-onopen ws) )
     (set! (.-onclose ws) #(println "close" %))
     (set! (.-onmessage ws) (fn [msg] (.send ws "baz")(.log js/console "msg" msg))))
   (assoc db :connected? false)))

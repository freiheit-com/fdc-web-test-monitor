(ns webtm.config)

(def +server-rest+ (atom ""))
(def +server-ws+ (atom ""))

(def *stats-token* (atom "test"))
(def *meta-token* (atom "test"))

(def debug?
  ^boolean js/goog.DEBUG)

(enable-console-print!)
;; (when debug?
;;   (reset! +server-rest+ "http://localhost:3001")
;;   (reset! +server-ws+ "ws://localhost:3001/ws/"))

(defn init [meta stats]
  (when (not-empty meta) (reset! *meta-token* meta))
  (when (not-empty stats) (reset! *stats-token* stats)))

(defn stats-token []
  @*stats-token*)

(defn meta-token []
  @*meta-token*)

(defn server-rest-url [path]
  (str @+server-rest+ path))

(defn server-ws-url
  ([] (server-ws-url ""))
  ([path]
  (str @+server-ws+ path)))

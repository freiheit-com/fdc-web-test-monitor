(ns webtm.tokens
  (:require [clojure.string :as string]))

(def +meta-cookie+ "meta-token")
(def +stat-cookie+ "stat-token")

(defn- empty-string->nil [val]
  (if (= "" (string/trim val))
    nil
    val))

(defn- read-val [cookie]
  (when cookie
    (empty-string->nil (nth (string/split cookie "=") 1))))

(defn- cookie-value [name]
  (let [cookie (some #(when (= 0 (.indexOf (string/trim %) name)) (string/trim %)) (string/split js/document.cookie ";"))]
    (read-val cookie)))

(defn- prompt-and-save-cookie [with-message cookie]
  (let [val (js/prompt with-message)]
    (set! js/document.cookie (str cookie "=" val))
    val))

(defn request-tokens! []
  {:meta-token (or (cookie-value +meta-cookie+) (prompt-and-save-cookie "Enter meta auth-token" +meta-cookie+))
   :stat-token (or (cookie-value +stat-cookie+) (prompt-and-save-cookie "Enter statistic auth-token" +stat-cookie+))})

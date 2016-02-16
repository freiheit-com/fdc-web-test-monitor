(ns webtm.db
  (:require [schema.core :as s :include-macros true]))

(def default-db
  {:meta {}
   :project {}})

"Returns information about all registered projects in the following format:
-> {\"projects\": [{\"project\": \"foo\",
                  \"subprojects\": [{\"subproject\": \"bar\",
                                   \"languages\": [{\"language\": \"java\"}, {\"language\": \"clojure\"}]},
                                  {\"subproject\": \"baz\", \"languages\": ...}"

(def Meta-Wire {(s/required-key "projects")
                [{(s/required-key "project") s/Str
                  (s/required-key "subprojects")
                  [{(s/required-key "subproject") s/Str
                    (s/required-key "languages")
                    [{(s/required-key "language") s/Str}]}]}]})

(def Meta [{:project s/Str :subprojects [{:subproject s/Str :languages [{:language s/Str}]}]}])

(defn parse-js
  [response]
  (let [real-js (clj->js (js->clj response)) ;; somehow the parser for ajax requests seems to be returning non-js/Objects
        data (js->clj real-js :keywordize-keys true)] ;; i think this is a bug and opts should be a map
    data))


(defn parse-meta
  "parse meta data RESPONSE into usable format"
  [response]
  (s/validate Meta-Wire response)
  (let [data (parse-js response)
        projects (get data :projects)]
    (s/validate Meta projects)
    projects))

(defn parse-project
  "parse meta data RESPONSE into usable format"
  [response]
  (parse-js response))

(ns clj-tumblr-summarizer.core
  (:require [org.httpkit.client :as http]
            [clojure.pprint :as pp]
            [clojure.data.json :as json]) )

(def api-key (slurp ".api-key"))

(def posts-url (str "http://api.tumblr.com/v2/blog/holyjak.tumblr.com/posts?offset=0&limit=1&api_key=" api-key)) ;; max limit=20

;; synchronous
(defn do-it []
  (let [{:keys [status headers body error] :as resp} @(http/get posts-url)]
    (if error
      (println "Failed, exception: " error)
      (json/pprint
       (json/read-str body)))))

;; See }
;; "updated":1429738599
;; posts []:
;; - date str / timestamp ms num
;; - "tags":["JavaScript"]
;; - ? post_url
;; - title
;; - URL type: url, description [html]
;; - "type":"link"
;; "total_posts":95

(defn -main [& args]
  (do-it))

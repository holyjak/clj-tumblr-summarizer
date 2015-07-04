(ns clj-tumblr-summarizer.core
  (:require [org.httpkit.client :as http]) )

(def api-key (slurp ".api-key"))

(def posts-url (str "http://api.tumblr.com/v2/blog/holyjak.tumblr.com/posts?offset=0&limit=1&api_key=" api-key)) ;; max limit=20

;; synchronous
(let [{:keys [status headers body error] :as resp} @(http/get posts-url)]
  (if error
    (println "Failed, exception: " error)
    (println "HTTP GET success: " body)))

;; See
;; "updated":1429738599
;; posts []:
;; - date str / timestamp ms num
;; - "tags":["JavaScript"]
;; - ? post_url
;; - title
;; - URL type: url, description [html]
;; - "type":"link"
;; "total_posts":95

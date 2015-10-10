(ns clj-tumblr-summarizer.core
  )

(defn log [& args]
  (binding [*out* *err*]
    (apply println args)))


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


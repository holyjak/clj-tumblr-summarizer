(ns clj-tumblr-summarizer.output
  (require [hiccup.core :as h]
           [clojure.data.json :as json]))

(def sample (json/read-str
             (slurp "sample-post.json")))

(defn item []
  (h/html
   [:article
    [:h5
     [:a {:href "http://example.com/aLink"} "A link"]]
    [:p "its description"]]))

(comment

  (get-in ["response" "posts" 0] sample)
  (def post0 (get-in sample ["response" "posts" 0]))
  (select-keys post0 ["url" "timestamp" "title" "type" "description"])

  )

(ns clj-tumblr-summarizer.output
  (require [hiccup.core :as h]))

(defn item []
  (h/html
   [:article
    [:h5
     [:a {:href "http://example.com/aLink"} "A link"]]
    [:p "its description"]]))

(item)

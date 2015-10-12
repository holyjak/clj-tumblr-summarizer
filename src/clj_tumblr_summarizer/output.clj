(ns clj-tumblr-summarizer.output
  (require [hiccup.core :as h]
           [clojure.data.json :as json]
           [clojure.edn :as edn]))

#_(def sample (json/read-str
                (slurp "sample-post.json")))
(def sample (edn/read-string
                (slurp "sample-post.edn")))

(defn link-item [{:strs [url title description]}]
  (h/html
   [:article
    [:h5
     [:a {:href url} title]]
    [:p description]]))

(comment

  (-> sample
     (get-in ["response" "posts" 0])
     (select-keys ["url" "timestamp" "title" "type" "description"])
     (link-item))

  )

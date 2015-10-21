(ns clj-tumblr-summarizer.output
  (require [hiccup.core :as h]
           [clojure.data.json :as json]
           [clojure.edn :as edn]
           [clojure.string :as str]))

#_(def sample (json/read-str
                (slurp "sample-post.json")))
(def sample (edn/read-string
                (slurp "sample-post.edn")))

(defn link-item [{:keys [url
                         description
                         post_url
                         tags
                         timestamp
                         title]}]
  (h/html
   [:article
    [:h5
     [:a {:href url} title]]
    [:p description]]))

(defn text-item [{:keys [body
                         post_url
                         tags
                         timestamp
                         title]}]
  (h/html
    [:article
     [:h5 title]
     [:p body]]))

(defn quote-item [{:keys [text
                          source
                          post_url
                          tags
                          timestamp
                          title]}]
  (h/html
    [:article
     [:blockquote text]
     [:p source]]))

(defn item [data]
  (condp = (:type data)
    "link" (link-item data)
    "text" (text-item data)
    "quote" (quote-item data)))

(comment

  (spit "out.html" (str/join "" (map item sample)))

  )

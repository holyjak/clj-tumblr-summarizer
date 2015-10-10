(ns clj-tumblr-summarizer.input
  (:require
    [clojure.core.async :as a :refer [chan >!! <!! close!]]
    [clojure.data.json :as json]
    [org.httpkit.client :as http]))

(defn send-posts [chan body]
  (let [posts (-> body
                  (json/read-str)
                  (get-in ["response" "posts"]))]
    (a/onto-chan chan posts true)))

;; synchronous
(defn fetch-posts [{:keys [chan api-key]}]
  (let [posts-url (str "http://api.tumblr.com/v2/blog/holyjak.tumblr.com/posts?offset=0&limit=1&api_key=" api-key)
        {:keys [status headers body error] :as resp} @(http/get posts-url)]
    (if error
      (throw (ex-info (.getMessage error) {:ctx (str "Fetching posts failed from " posts-url)}))
      (send-posts chan body)
      )))

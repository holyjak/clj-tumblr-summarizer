(ns clj-tumblr-summarizer.input
  (:require
    [clojure.core.async :as a :refer [chan >!! <!! close!]]
    [clojure.data.json :as json]
    [org.httpkit.client :as http]))

(defn posts->chan [chan body]
  (let [posts (-> body
                  (json/read-str :key-fn keyword)
                  (get-in [:response :posts]))]
    (if-not (empty? posts)
      (a/onto-chan chan posts true)
      (close! chan))))

;; synchronous
(defn fetch-posts-batch [{:keys [chan api-key offset]}]
  (println ">>> fetch-posts-batch" offset)
  (let [posts-url (str
                    "http://api.tumblr.com/v2/blog/holyjak.tumblr.com/posts?offset="
                    offset
                    "&limit=20&api_key="
                    api-key)
        {:keys [status headers body error] :as resp} @(http/get posts-url)]
    (if-not error
      (posts->chan chan body)
      (throw
        (ex-info
          (.getMessage error)
          {:ctx (str "Fetching posts failed from " posts-url)})))))

(defn fetch-posts [{:keys [chan api-key] :as config}]
  (loop [offset 0]
    ;; TODO Stop if we crossed into another month
    ;; (or let the receiver close the channel when it is so?)
    (when
      (fetch-posts-batch
        (assoc config :offset offset))
      (recur (+ 20 offset)))))

(comment
  (fetch-posts {:chan (chan), :api-key (slurp ".api-key")})
  )
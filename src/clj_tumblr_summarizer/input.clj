(ns clj-tumblr-summarizer.input
  (:require
    [clojure.core.async :as a :refer [chan >!! <!! close!]]
    [clojure.data.json :as json]
    [org.httpkit.client :as http]))

(def api-key (slurp ".api-key"))

;; TODO
;; Keep on fetching a single page and putting it onto a channel
;; until exhausted or the destination channel is closed by the consumer
;; (Thus the consumer decides when the stop by closing the channel).

(defn posts->chan [chan body]
  (let [posts (-> body
                  (json/read-str :key-fn keyword)
                  (get-in [:response :posts]))]
    (if-not (empty? posts)
      (a/onto-chan! chan posts false))))

(defn fetch-posts-raw
  "Fetch 20 posts starting at the given offset, return the body (string) or throw
  Params:
   - `href` is path + query params, such as /v2/blog/holyjak.tumblr.com/posts?npf=true"
  [href]
  {:pre [href]}
  (let [posts-url (str ; before=int - Returns posts published earlier than a specified Unix timestamp, in seconds.
                    "http://api.tumblr.com" href "&api_key=" api-key)
        {:keys [status headers body error] :as resp} @(http/get posts-url)]
    (println "DBG fetching" href)
    ;; NOTE: We get OK with empty posts when no more posts

    ;; FIXME Add retry w/ exponential backoff, esp. when we hit the rate limit

    (if-not error
      body
      (throw
        (ex-info
          (.getMessage error)
          {:ctx (str "Fetching posts failed from " posts-url)})))))

(defn parse-body [body]
  (let [{:keys [_links posts total_posts]}
        (:response (json/read-str body :key-fn keyword))]
    {:posts posts
     :total-posts total_posts
     :next-href (-> _links :next :href)}))

(defn async-fetch-all-posts
  "Fetches all the posts, starting from the newest, until exhausted or the
  `dst` channel is closed.

  Returns a channel that will contain either `:success` or an `Exception`."
  [dst]
  (a/thread
    (try
      (loop [href "/v2/blog/holyjak.tumblr.com/posts?npf=true"]
        (let [{:keys [posts next-href]} (parse-body (fetch-posts-raw href))
              [post & more-posts] posts
              dst-open? (when post (a/>!! dst post))]
          (when (empty? posts)
            (a/close! dst))
          (<!!                                              ; back-pressure
            (a/onto-chan! dst more-posts false))
          (if (and dst-open? next-href)
            (recur next-href)
            :success)))
      (catch Exception e
        (println "ERROR fetch-posts:" e)
        e)
      (finally
        (a/close! dst)))))



;; RESPONSE FORMAT FOR A POST
;; :tags ["clojure" "library" "clojurescript"]
;; :date "2020-11-04 16:37:44 GMT"
;:content [{:type "text"
;           :text "luciodale/arco: Instant formatter to render time that has passed or is left since/to a certain event"
;           :subtype "heading2"}
;          {:type "text"
;           :text "Configurable, support for multiple languages. "}]
; :state "published"
; :timestamp 1604507864 -> * 1000 (Date.)
; :id 633872438054256640

(comment

  (pipeline (print-ch))



  (def ch (chan))

  (let [ch (chan)]
    (fetch-posts! {:chan (chan), :api-key api-key})
    (loop [v :START]
      (println "RECEIVED" v)
      (recur (<!! ch))))

  (def posts
    (-> (<!! (fetch-posts-batch 0))
        (body->posts)))

  (tap> posts)
  (tap> (first posts))

  (posts->chan ch body)
  (def res (<!! (a/into [] ch)))
  (count res) ; => 20


  nil)

(ns clj-tumblr-summarizer.input
  (:require
    [clojure.core.async :as a :refer [chan >!! <!! close!]]
    [clojure.data.json :as json]
    [clj-tumblr-summarizer.async-utils :as au]
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

(defn fetch-post-batch-raw
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

(defn start-post-batch-producer
  "A producer for use with `async-iterate`, returning batches of posts
  in the form defined by [[parse-body]].

  Returns the `out` channel"
  [in out]
  (a/go-loop []
    (if-let [href (:next-href (a/<! in))]
      (do (a/>! out (try
                      (parse-body (fetch-post-batch-raw href))
                      (catch Exception e
                        {:error e})))
          (recur))
      (do (a/close! out)
          (println "DBG [start-]post-batch-producer: done"))))
  out)

(defn fetch-posts-async!
  "Keep fetching posts and putting them onto `dst` until
  there are no more or the `stop-signal` channel is closed.
  Returns the `dst` channel immediately."
  [dst stop-signal]
  ;; NOTE Fetches one page too many when stop-signal sent due to the
  ;; (unavoidable?) 1 buffer of the `unrolled-posts-ch` :-(
  (let [producer-in  (chan)
        producer-out (chan)
        unrolled-posts-ch (chan 1 (comp
                                    (map :posts)
                                    cat))]
    (a/go
      (a/<! stop-signal)
      (println "DBG fetch-posts-async! received stop signal, closing producer-in")
      (a/close! producer-in))
    (-> (start-post-batch-producer producer-in producer-out)
        (au/async-iterate producer-in {:next-href "/v2/blog/holyjak.tumblr.com/posts?npf=true"})
        (a/pipe unrolled-posts-ch)
        (a/pipe dst))))

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

  (let [dst (chan 1 (take 25))
        stop (chan)]
    (println "TST Fetching...")
    (println "TST Retrieved"
             (count (a/<!! (a/into [] (fetch-posts-async! dst stop))))
             "posts")
    (println "TST Closing dst...")
    (a/close! stop))

  nil)

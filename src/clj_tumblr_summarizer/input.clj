(ns clj-tumblr-summarizer.input
  "Input - reading newest posts from Tumblr"
  (:require
    [clojure.core.async :as a :refer [chan >!! <!! close!]]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clj-tumblr-summarizer.async-utils :as au]
    [org.httpkit.client :as http])
  (:import (java.io PushbackReader)))

(def api-key (slurp ".api-key"))

(defn fetch-post-batch-raw
  "Fetch 20 posts starting at the given offset, return the body (string) or throw
  Params:
   - `href` is path + query params, such as /v2/blog/holyjak.tumblr.com/posts?npf=true"
  [href]
  {:pre [href]}
  (let [posts-url (str ; before=int - Returns posts published earlier than a specified Unix timestamp, in seconds.
                    "http://api.tumblr.com" href "&api_key=" api-key)
        {:keys [status headers body error] :as resp} @(http/get posts-url)]
    (println "DBG fetched" href "->" status error)
    ;; NOTE: We get OK with empty posts when no more posts

    ;; FIXME Add retry w/ exponential backoff, esp. when we hit the rate limit

    (cond

      error
      (throw
        (ex-info
          (.getMessage error)
          {:ctx (str "Fetching posts failed from " posts-url)}))

      (>= status 300)
      (throw
        (ex-info
          (str "Got non-2xx HTTP status " status ", body: " body)
          {:posts-url posts-url
           :status status
           :body body}))

      :else
      body)))

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
  "Keep fetching posts (newest first) and putting them onto
  `dst` until there are no more or the `stop-signal` channel
  is closed. Returns the `dst` channel immediately."
  [blog-name dst stop-signal]
  ;; NOTE Fetches one page too many when stop-signal sent due to the
  ;; (unavoidable?) 1 buffer of the `unrolled-posts-ch` :-(
  (let [producer-in  (chan)
        producer-out (chan)
        unrolled-posts-ch (chan 1 (comp
                                    (map :posts)
                                    cat))]
    (a/go ;; FIXME Make this stop also when the producer stops itself?
      (a/<! stop-signal)
      (println "DBG fetch-posts-async! received stop signal, closing producer-in")
      (a/close! producer-in))
    (-> (start-post-batch-producer producer-in producer-out)
        (au/async-iterate producer-in {:next-href (str "/v2/blog/" blog-name ".tumblr.com/posts?npf=true")})
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

;; ---------------------------------------- file system storage

(defn data-files->posts-chan
  "Read previously dumped posts from the .edn files.
  Returns a channel that the posts will be pushed to (as maps)."
  []
  (let [tx-ch (chan 1 (comp
                        (filter #(clojure.string/ends-with? (.getName %) ".edn"))
                        (map clojure.java.io/reader)
                        (map #(PushbackReader. %))
                        (map clojure.edn/read)
                        #_(map (juxt :date :slug))))]

    (a/thread
      (run!
        #(a/>!! tx-ch %)
        (file-seq (clojure.java.io/file "data")))
      (a/close! tx-ch))
    tx-ch))

(defn timestamp->instant
  "Blog timestamp to java.time.Instant"
  [timestamp]
  (when timestamp
    (java.time.Instant/ofEpochSecond timestamp)))

(defn max-post-timestamp-ch
  "Of all the posts on the channel, find the max (newest) timestamp. Returns a channel that will contain the value
  (or 0). See also [[timestamp->instant]]"
  [posts-ch]
  (a/go
    (a/<! (->> (chan 1 (map :timestamp))
               (a/pipe posts-ch)
               (a/reduce max 0)))))

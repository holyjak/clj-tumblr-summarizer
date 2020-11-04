(ns clj-tumblr-summarizer.input
  (:require
    [clojure.core.async :as a :refer [chan >!! <!! close!]]
    [clojure.data.json :as json]
    [org.httpkit.client :as http]))

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

(defn fetch-posts-batch*
  "Fetch 20 posts starting at the given offset, return the body (string) or throw"
  [api-key offset]
  {:pre [api-key offset]}
  (let [posts-url (str
                    "http://api.tumblr.com/v2/blog/holyjak.tumblr.com/posts?offset="
                    offset
                    ; before=int - Returns posts published earlier than a specified Unix timestamp, in seconds.
                    "&limit=20&npf=true&api_key="
                    api-key)
        {:keys [status headers body error] :as resp} @(http/get posts-url)]
    (if-not error
      body
      (throw
        (ex-info
          (.getMessage error)
          {:ctx (str "Fetching posts failed from " posts-url)})))))

;; synchronous
(defn fetch-posts-batch
  "Fetch the newest posts with the given offset, return a channel with the body."
  [{:keys [api-key offset] :or {offset 0}}]
  (println ">>> fetch-posts-batch" offset)
  (a/thread
    (fetch-posts-batch* api-key offset)))

;(defn fetch-posts!
;  "Fetch all the posts as per arguments. Blocking!"
;  [{:keys [chan api-key] :as config}]
;  (loop [offset 0]
;    ;; TODO Stop if we crossed into another month
;    ;; (or let the receiver close the channel when it is so?)
;    (when
;      (fetch-posts-batch
;        (assoc config :offset offset))
;      (recur (+ 20 offset)))))


(defn body->posts [body]
  (-> body
      (json/read-str :key-fn keyword)
      (get-in [:response :posts])))

(defn fetch-posts
  "Fetches the posts, putting each onto the `dst` channel and closing it when done."
  [api-key dst]
  (let [src      (chan)
        xform    (comp
                   (map body->posts)
                   cat)
        posts-ch (chan 1 xform)]
    (-> (fetch-posts-batch {:api-key api-key, :chan src})
        (a/pipe posts-ch))
    (a/pipe posts-ch dst)
    #_(a/pipe posts-ch (print-ch))))

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

  (pipeline api-key (print-ch))

  (defn print-ch []
    (let [ch (a/chan)]
      (a/go-loop []
        (if-let [val (a/<! ch)]
          (do (println "VAL: " val)
              (recur))
          (println "CLOSED dst")))
      ch))

  (def ch (chan))

  (let [ch (chan)]
    (fetch-posts! {:chan (chan), :api-key api-key})
    (loop [v :START]
      (println "RECEIVED" v)
      (recur (<!! ch))))

  (-> (fetch-posts-batch (slurp ".api-key") 0)
      (json/read-str :key-fn keyword)
      (get-in [:response :posts]))

  (tap> posts)
  (tap> (first posts))

  (posts->chan ch body)
  (def res (<!! (a/into [] ch)))
  (count res) ; => 20


  nil)

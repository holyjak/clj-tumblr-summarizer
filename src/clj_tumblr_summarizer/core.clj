(ns clj-tumblr-summarizer.core
  (:require [org.httpkit.client :as http]
            [clojure.pprint :as pp]
            [clojure.data.json :as json]
            [clojure.core.async :as a :refer [chan >!! <!! close!]]))

(def api-key (slurp ".api-key"))
(def tumblr-max-limit 20)

(def posts-url (str "http://api.tumblr.com/v2/blog/holyjak.tumblr.com/posts?offset=0&limit=1&api_key=" api-key)) ;; max limit=20

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread throwable]
     (binding [*out* *err*]
       (println "UNCAUGHT ERROR ON A THREAD:" (.getMessage throwable))))))

(defn log [& args]
  (binding [*out* *err*]
    (apply println args)))


(defn send-posts [chan body]
  (let [posts (-> body
                 (json/read-str)
                 (get-in ["response" "posts"]))]
    (a/onto-chan chan posts true)))

;; synchronous
(defn fetch-posts [chan]
  (let [{:keys [status headers body error] :as resp} @(http/get posts-url)]
    (if error
      (throw (ex-info (.getMessage error) {:ctx (str "Fetching posts failed from " posts-url)}))
      (send-posts chan body)
      )))

(defn print-receiver [chan]
  (a/thread
    (loop []
        (if-let [post (<!! chan)]
          (do
            (json/pprint post)
            (recur))
          (log "print-receiver: DONE, no more input")))))

;; See }
;; "updated":1429738599
;; posts []:
;; - date str / timestamp ms num
;; - "tags":["JavaScript"]
;; - ? post_url
;; - title
;; - URL type: url, description [html]
;; - "type":"link"
;; "total_posts":95

(defn -main [& args]
  (def post-chan (chan tumblr-max-limit))

  (fetch-posts post-chan)
  ;; Start the reader, wait for it to finish before shutting down
  (<!! (print-receiver post-chan)))

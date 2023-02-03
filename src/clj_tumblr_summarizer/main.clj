(ns clj-tumblr-summarizer.main
  "The entry point for running the tool, fetching, storing, and
  processing new Tumblr posts."
  (:require
   [clj-tumblr-summarizer.async-utils :as au]
   [clj-tumblr-summarizer.input :as in]
   [clj-tumblr-summarizer.output :as out]
   [clj-tumblr-summarizer.storage.fs :as fs]
   [clj-tumblr-summarizer.time :as t]
   [clojure.core.async :as a :refer [chan >!! <!! close!]]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.pprint :as pp]
   [clojure.string :as str]))

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread throwable]
      (binding [*out* *err*]
        (println "UNCAUGHT ERROR ON A THREAD:" (.getMessage throwable))))))

(defn store-new-posts
  "Fetch all posts from the given Tumblr blog and dump them into files."
  [blog-name]
  (let [previous-ts      (in/max-stored-post-timestamp)
        posts-ch (chan 1 (take-while #(> (:timestamp %)
                                         previous-ts)))
        stop (chan)]
    (in/fetch-posts-async! blog-name posts-ch stop)
    (au/spit-chan posts-ch (fn [{:keys [id timestamp]}]
                             (str timestamp "-" id))
      :timestamp)
    (a/close! stop)))

(defn -main [& _]
  (let [blog-name (or (some-> (slurp "config.edn") edn/read-string :blog)
                    (throw (IllegalStateException. "Could not find :blog inside ./config.edn with the name of the target Tumblr blog")))]
    (store-new-posts blog-name)))

(defn summarize
  "Summarize posts for the previous month into `summary-<date>.html`"
  ([] (summarize nil))
  ([{:keys [date]}]
    (let [ts-range (if date 
                     (t/any-month date)
                     (t/previous-month))
          md+yr (->> ts-range first t/epoch-ts->zdt
                     ((juxt t/zdt->month t/zdt->year))
                     (str/join "-"))
          fname (str "summary-" md+yr ".html")
          posts (seq (fs/posts-in-time-range ts-range))]
      (if posts
        (do
          (->> posts
               (out/render-posts fname))
          (println (count posts) "posts summarized into" fname))
        (println "No posts in " md+yr "Have you forgotten to download them?")))))

(comment

  (:blog (slurp "config.edn"))
  (slurp "config.edn")

  (summarize)
  ((requiring-resolve 'portal.api/close))
  (store-new-posts "holyjakXXX")
  nil
  )

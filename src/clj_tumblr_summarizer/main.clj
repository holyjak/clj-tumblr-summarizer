(ns clj-tumblr-summarizer.main
  "The entry point for running the tool, fetching, storing, and
  processing new Tumblr posts."
  (:require [clojure.pprint :as pp]
            [clojure.data.json :as json]
            [clojure.core.async :as a :refer [chan >!! <!! close!]]
            [clj-tumblr-summarizer.async-utils :as au]
            [clj-tumblr-summarizer.output :as out]
            [clj-tumblr-summarizer.input :as in]))

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread throwable]
      (binding [*out* *err*]
        (println "UNCAUGHT ERROR ON A THREAD:" (.getMessage throwable))))))

(defn store-new-posts
  "Fetch all posts from the given Tumblr blog and dump them into files."
  [blog-name]
  (let [previous-ts      (<!! (in/max-post-timestamp-ch (in/data-files->posts-chan)))
        posts-ch (chan 1 (take-while #(> (:timestamp %)
                                         previous-ts)))
        stop (chan)]
    (in/fetch-posts-async! blog-name posts-ch stop)
    (au/spit-chan posts-ch (fn [{:keys [id timestamp]}]
                             (str timestamp "-" id)))
    (a/close! stop)))

(defn -main [& args]
  (let [blog-name (or (first args) "holyjak")]
    (store-new-posts blog-name)))

(comment
  (store-new-posts "holyjak")
  nil)

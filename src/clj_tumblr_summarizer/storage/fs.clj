(ns clj-tumblr-summarizer.storage.fs
  "Acess to posts stored on the file system"
  (:require 
   [babashka.fs :as fs]
   [clojure.edn :as edn])
  (:import (java.nio.file Path)))

(def data-dir "./data")

(defn fname->ts [file-name]
  (-> (re-find #"out(\d+)" file-name) second Integer/parseInt))

(defn path->ts ^long [^Path file-path]
  (-> (.getFileName file-path) str fname->ts))

(defn posts-in-time-range 
  "Return a seq of all posts published withing the given `time-range`
  sorted by date.

  `time-range` - in epoch seconds, [inclusive, exclusive)"
  [[start-sec end-sec]]
  (->>
   (fs/list-dir data-dir
     #(<= start-sec (path->ts %) (dec end-sec)))
   (sort-by path->ts)
   (map (comp edn/read-string slurp str))))

(comment
  (->> (posts-in-time-range [1638316800 1640995200])
       ((juxt first last))
       (map :date))
  )
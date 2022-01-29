(ns clj-tumblr-summarizer.storage.fs
  "Acess to posts stored on the file system"
  (:require 
   [clj-tumblr-summarizer.time :as t]
   [babashka.fs :as fs]
   [clojure.edn :as edn])
  (:import (java.nio.file Path)))

(def data-dir "./data")
(def max-timestamp-path (fs/path data-dir "max-timestamp"))

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

(defn read-max-timestamp []
  (let [f max-timestamp-path]
    (when (fs/readable? f)
      (-> (fs/read-all-bytes f)
          (String.)
          (Long/parseLong)
          (doto (->> t/epoch-ts->zdt str (println "DBG: Max timestamp from" (str f) "is")))))))

(defn write-max-timestamp [ts-epoch-sec]
  (spit
    (fs/file max-timestamp-path)
    ts-epoch-sec))

(defn write-post [post fname]
  (when-not (fs/directory? data-dir)
    (fs/create-dirs data-dir))
  (binding [*print-length* nil, *print-level* nil]
    (spit (fs/file data-dir fname)
      (pr-str post))))

(comment
  (->> (posts-in-time-range [1638316800 1640995200])
       ((juxt first last))
       (map :date))
  )
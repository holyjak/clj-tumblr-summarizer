(ns clj-tumblr-summarizer.async-utils
  (:require
    [clojure.core.async :as a :refer [<! <!! >!]]))

(defn chan-while
  "Like `take-while` applied to a channel, closing
  both the src and dst channels when the pred-icate
  returns false."
  [src dst pred]
  (let [filtered (a/chan 1 (take-while pred))]
    (a/pipe src filtered)
    (a/go-loop []
             (if-let [val (<! filtered)]
               (and (>! dst val)
                    (recur))
               (do (a/close! src)
                   (a/close! dst))))))

(defn spit-chan
  "Write N-th value into ./out[00N].edn."
  ([ch] (spit-chan ch 0))
  ([ch idx]
   (when-let [v (<!! ch)]
     (binding [*print-length* nil, *print-level* nil]
       ;; FIXME Handle errors
       (spit (format "out%03d.edn" idx)
             (pr-str v)))
     (recur ch))))

(comment
  (def src (a/to-chan! [1 3 5 2 4 6]))

  (defn print-ch []
    (let [ch (a/chan)]
      (a/go-loop []
                 (if-let [val (a/<! ch)]
                   (do (println "VAL: " val)
                       (recur))
                   (println "CLOSED dst")))
      ch))

  (chan-while src (print-ch) odd?)

  (a/poll! *1)
  (a/close! src)
  nil)

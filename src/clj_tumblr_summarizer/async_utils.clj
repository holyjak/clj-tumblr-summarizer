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
  "Write N-th value into ./out(id-fn value).edn."
  ([ch id-fn]
   (when-let [v (<!! ch)]
     (binding [*print-length* nil, *print-level* nil]
       ;; FIXME Handle errors
       (spit (format "data/out%s.edn" (id-fn v))
             (pr-str v)))
     (recur ch id-fn))))

(defn tap-ch []
  (let [ch (a/chan)]
    (a/go-loop []
      (if-let [val (a/<! ch)]
        (do (tap> [:print-ch val])
            (recur))
        (tap> [:print-ch :CLOSED])))
    ch))

#_(defn async-iteration
    [from-process
     to-process
     output-channel
     & {:keys [values-selector some? init]
        :or {values-selector vector
             some? some?}}]
    (a/>! to-process init)
    (loop [value (a/<! from-process)]
      (if (some? value)
        (do
          (doseq [item (values-selector value)]
            (a/>! output-channel item))
          (a/>! to-process value))
        (a/close! output-channel))))

(comment
  (def src (a/to-chan! [1 3 5 2 4 6]))

  (spit-chan (a/to-chan! [{:some "map" :is "here"}]))



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

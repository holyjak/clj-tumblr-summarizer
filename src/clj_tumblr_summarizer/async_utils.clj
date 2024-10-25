(ns clj-tumblr-summarizer.async-utils
  "Utils for core async."
  (:require
   [clojure.core.async :as a :refer [<! >! <!! >! go go-loop close! chan]]
   [clojure.java.io :as io]
   [clojure.test :refer [is]]
   [clj-tumblr-summarizer.storage.fs :as fs]))

#_(defn chan-while
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

(defn async-iterate
  "Similar to `clojure.core/iterate` but working with a producer process rather then a function.
  Returns a channel with the produced values that closes when the producer closes.

  A producer reads the previously produced value (starting with `init`) from `to-producer`
  and writes a new value to `from-producer`."
  [from-producer
   to-producer
   init]
  (go (>! to-producer init))
  (let [produced-vals (chan)]
    (go-loop [v (<! from-producer)]
      (if (and v (>! produced-vals v))
        (do
          (a/put! to-producer v)
          (recur (<! from-producer)))
        (do (run! close! [to-producer produced-vals])
          (println "DBG async-iterate done, closing all"))))
    produced-vals))

(comment
  (let [pin (chan), pout (chan)
        proc (-> (a/pipe pin (chan 1 (comp (map inc) (take 3))))
                 (a/pipe pout))]
    (assert (is (= [1 2 3]
                   (<!! (a/into [] (async-iterate pout pin 0))))))
    (let [[v c] (a/alts!! [pin (a/to-chan! [:closed]) :priority])]
      (assert (is (= v nil)))
      (assert (is (= pin c)))))
  nil)

(defn spit-chan
  "Write items into files, N-th value into ./data/out(id-fn value).edn. Blocking.
   If `timestamp-fn` is provide then it will also store the max timestamp."
  ([ch id-fn timestamp-fn]
    (loop [ch ch, max-ts nil, cnt 0]
      (if-let [v (<!! ch)]
        (do
          (println "spit-chan -> " (format "data/out%s.edn" (id-fn v)))
          (fs/write-post v (format "out%s.edn" (id-fn v)))
          (recur ch (when (and timestamp-fn (timestamp-fn v))
                      (cond-> (timestamp-fn v)
                        max-ts (max max-ts)))
                 (inc cnt)))
        (do (some-> max-ts fs/write-max-timestamp)
            {:written-cnt cnt, :new-max-ts max-ts})))))

(defn tap-ch
  "(Troubleshooting) Make a channel that puts every value inserted into it onto `tap>` and also reports when closed."
  []
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

  (spit-chan (a/to-chan! [{:some "map" :is "here"}]) (constantly nil))



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

(ns clj-tumblr-summarizer.main
  (:require [clojure.pprint :as pp]
            [clojure.data.json :as json]
            [clojure.core.async :as a :refer [chan >!! <!! close!]]
            [clj-tumblr-summarizer.core :refer [log]]
            [clj-tumblr-summarizer.output :as out]
            [clj-tumblr-summarizer.input :as in]))

(def api-key (slurp ".api-key"))
(def tumblr-max-limit 20)                                   ;; TODO mv to input

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread throwable]
      (binding [*out* *err*]
        (println "UNCAUGHT ERROR ON A THREAD:" (.getMessage throwable))))))

(defn print-receiver [chan]
  (a/thread
    (loop []
      (if-let [post (<!! chan)]
        (do
          ;; Re-opening+closing is inefficient but who cares?
          (spit "out.html" (out/item post) :append true)
          (spit "out.edn"
                (prn-str
                  (select-keys post
                               [:post_url                   ;; all
                                :tags                       ;; all
                                :timestamp                  ;; all?
                                :title                      ;; all
                                :type                       ;; all "link" "text" "quote" "video" ...
                                :description                ;; link
                                :excerpt                    ;; link; "sub-title"
                                ;; link: + :author :publisher :photos
                                :body                       ;; on a text
                                :text                       ;; quote
                                :source                     ;; quote
                                ;; TODO What are notes?
                                ]))
            :append true)
          (recur))
        (log "print-receiver: DONE, no more input")))))

;; TODO Create an output stream, pass it to (out/output-post post) then close

(defn -main [& args]
  (let [post-chan (chan tumblr-max-limit)]
    ;; Reset files
    (spit "out.html" "")
    (spit "out.edn" "")
    (in/fetch-posts {:chan post-chan, :api-key api-key})
    ;; Start the reader, wait for it to finish before shutting down
    (<!! (print-receiver post-chan))))


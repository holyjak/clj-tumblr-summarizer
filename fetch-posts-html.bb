;; For each of the cached post, fetch the HTML of the original post 
;; (ignoring the surrounding page) and put it inside a map id -> html stored
;; inside the file `orig-posts.edn`
(require
  '[babashka.fs :as fs]
  '[babashka.pods :as pods])

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9") ; provides Hickory
(require
  '[pod.retrogradeorbit.bootleg.utils :as bootleg]
  '[pod.retrogradeorbit.bootleg.enlive :as enlive]
  '[pod.retrogradeorbit.hickory.select :as s]
  '[pod.retrogradeorbit.hickory.render :refer [hickory-to-html]])

(def post-id->url
  (->>
   (fs/list-dir "data")
   (map fs/file)
   (map slurp)
   (map clojure.edn/read-string)
   (map (juxt :id :post_url))
   (into {})))

(defn url->post-html [url]
  (let [html-hickory
        (->> (bootleg/convert-to (slurp url) :hickory-seq)
             (remove string?)
             (filter (comp #{:html} :tag))
             first)]
    (->> html-hickory
         (s/select (s/and (s/tag :section) (s/class :post)))
         first
         hickory-to-html)))

;; 90s for ± 700 posts
(time
  (->> post-id->url
       (pmap (fn [[id url]]
               [id (url->post-html url)]))
       vec
       (pr-str)
       (spit "orig-posts.edn")))
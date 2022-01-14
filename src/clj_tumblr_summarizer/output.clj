(ns clj-tumblr-summarizer.output
  "Produce a page summarizing your Tumblr posts of the month."
  (:require
   [hiccup.core :as h]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.java.io :as io]))

(defn unredirect [url]
  (cond
    (str/starts-with? url "https://t.umblr.com/redirect?z=")
    (-> url (subs 31) (str/split #"&") first (java.net.URLDecoder/decode "ascii"))

    (str/starts-with? url "https://href.li/?")
    (subs url 17)

    :else url))

(defn segment-text
  "Segment the `text` into as-is strings and pairs of `[substring formatting]`"
  [text breakpoints]
  (loop [pos 0
         [{:keys [start end] :as point} & points] (sort-by :start breakpoints)
         res []]
    (when point
      (assert (some-> start int?) (str "Unexpected formatting w/o start: " point))
      (assert (some-> end int?) (str "Unexpected formatting w/o end: " point)))
    (if point
      (recur
        end
        points
        (conj res
          (subs text pos start)
          [(subs text start end) point]))
      (remove empty? (conj res (subs text pos))))))


(defn apply-formatting [text {:keys [type] :as formatting}]
  (assert (= type "link")
    (str "Unsupportid formatting type for :text content: " type " in " formatting))
  [:a {:href (unredirect (:url formatting))} text])

(defn format-text-content [text formatting]
  (->> (segment-text text formatting)
       (map #(cond->> %
               (vector? %) (apply apply-formatting)))))

(defn render-text-block [{mytxt :text, :keys [formatting]}]
  (list [:br] (cond-> mytxt
                (seq formatting) (format-text-content formatting))))

(defn render-post [{:keys [content id layout summary tags type] :as post}]
  (assert (= type "blocks") (str " - :type " type " not supported yet"))
  (assert (< (count layout) 2) (str " - layout len " (count layout) " > 1 not supported"))
  (when-let [layt (-> layout first :type)]
    (assert (= "rows" (-> layout first :type)) (str " - unsupported layout type " layt)))
  (when-let [bad-blocks (->> layout first :display
                             (keep-indexed
                               (fn [idx {:keys [blocks]}] (when-not (= [idx] blocks) [:!= [idx] blocks])))
                             seq)]
    (throw (ex-info (str "Unsupported layout in " id (pr-str layout))
             {:id id, :layout layout, :bad bad-blocks})))
  (let [{:strs [link text] :as all} (group-by :type content)
        {:keys [url title description]} (first link)
        unsupported (-> all keys set (set/difference #{"link" "text"}))]
    (assert (empty? unsupported)
      (str " - unsupported content types found: " unsupported))
    (assert (< (count link) 2) (str " - ma 1 type=link content supported, got more " link))
    (when (not= title summary)
      (println "WARN: (not= title summary)" title "!=" summary))
    [:div
     [:p [:a {:href (unredirect url)} summary]
      " [" (str/join "," tags) "]"
      " - "
      description
      (map text render-text-block)]]))

(comment

  (def posts
    (->>
     (.listFiles (io/file "data")
                 (reify java.io.FileFilter
                   (accept [_ f]
                     (and (.isFile f) (str/ends-with? (.getName f) ".edn")))))
     seq
     (map #(edn/read-string (slurp %)))
   ;; TODO: does lways summary == content[t=link].title ?
     (map #(select-keys % [:content :id :layout :summary :tags :timestamp :type]))))

  (def meander (->> posts (filter #(= 186840427164 (:id %))) first))
  (def meander2
    {:content [{:type "link"
                :url "https://t.umblr.com/redirect?z=http%3A%2F%2Ftimothypratley.blogspot.com%2F2019%2F01%2Fmeander-answer-to-map-fatigue.html&t=OThhODBiN2RkOWQ0NmYwNjQ4ODJhNzEzMWZkZGZlZjU0ZmE5NzgxYyxkOTQ3OWMzZjU5YTNiZWVmNjM3NjBlZDBhM2VjODQwZWQyZjk1ZDc2"
              ;:display_url "https://t.umblr.com/redirect?z=http%3A%2F%2Ftimothypratley.blogspot.com%2F2019%2F01%2Fmeander-answer-to-map-fatigue.html&t=OThhODBiN2RkOWQ0NmYwNjQ4ODJhNzEzMWZkZGZlZjU0ZmE5NzgxYyxkOTQ3OWMzZjU5YTNiZWVmNjM3NjBlZDBhM2VjODQwZWQyZjk1ZDc2", 
                :title "Meander: The answer to map fatigue"
                :description "Programing in Clojure and ClojureScript"
              ;:site_name "timothypratley.blogspot.com", 
                #_#_:poster [{:media_key "6cf6626447e26b1700b2d59184b9834f:c93a72bd99c2bbdc-71", :type "image/jpeg", :width 259, :height 136, :url "https://64.media.tumblr.com/6cf6626447e26b1700b2d59184b9834f/c93a72bd99c2bbdc-71/s400x600/e0b2ba95d3e057f0db0b7a2f32610b9d15588e57.jpg"}]}
               {:type "text"
                :text "Meander is a very nice-looking library for declarative data transformations in
 Clojure, using pattern matching and logic-programming-like \"unification\", yielding a very clean, concise, and clear code. ♥️"
                :formatting [{:type "link", :start 0, :end 7, :url "https://href.li/?https://github.com/noprompt/meander/blob/delta/README.md"}]}]
     :id 186840427164
     :layout [{:type "rows", :display [{:blocks [0]} {:blocks [1]}]}]
     :summary "Meander: The answer to map fatigue"
     :tags ["clojure" "library" "data processing"]
     :timestamp 1565191242
     :type "blocks"})
  
  (def failed
    (->> posts
         (mapv
           #(try (render-post %)
              nil
              (catch Throwable e
                [:FAIL (:id %) (ex-message e)])))
         (remove nil?)))
  
  (count posts) ;; => 791
  (count failed) ;; => 101
  (->> failed (map #(nth % 2)) set count) ; 5 uniq errs
  ;; TODO: Content type "video" "image" "audio"
  (->> failed 
       (map #(nth % 2)) 
       (group-by identity)
       (map (fn [[k v]] [k (count v)]))
       (sort-by second)
       reverse
       (clojure.pprint/pprint))
  (first failed)
  (tap> failed)



  ()

  (pr (render-post meander2))
  (tap> (with-meta (render-post meander2)
          {:portal.viewer/default :portal.viewer/hiccup}))
  )
  (tap> (with-meta (render-post meander2) 
          {:portal.viewer/default :portal.viewer/tree}))


  

#_(def sample (json/read-str
                (slurp "sample-post.json")))
;(def sample (edn/read-string
;                (slurp "sample-post.edn")))
;
;(defn link-item [{:keys [url
;                         description
;                         post_url
;                         tags
;                         timestamp
;                         title]}]
;  (h/html
;   [:article
;    [:h5
;     [:a {:href url} title]]
;    [:p description]]))
;
;(defn text-item [{:keys [body
;                         post_url
;                         tags
;                         timestamp
;                         title]}]
;  (h/html
;    [:article
;     [:h5 title]
;     [:p body]]))
;
;(defn quote-item [{:keys [text
;                          source
;                          post_url
;                          tags
;                          timestamp
;                          title]}]
;  (h/html
;    [:article
;     [:blockquote text]
;     [:p source]]))
;
;(defn item [post]
;  (condp = (:type post)
;    "link" (link-item post)
;    "text" (text-item post)
;    "quote" (quote-item post)))

(comment

  (spit "out.html" (str/join "" (map item sample))))



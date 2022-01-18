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


(defn subrange? [[s1 s2] [b1 b2]]
  (and (>= s1 b1) (<= s2 b2)))

(defn subranges-first-compare
  "Compare for ranges (i.e. `[<from> <to>]`) where subranges come before their parent"
  [r1 r2]
  (cond
    (subrange? r1 r2) -1
    (subrange? r2 r1) +1
    :else
    (compare r1 r2)))

(defn ->formatting-node [subtext offset {:keys [start end] :as formatting-elm}]
  {:pre [(>= start offset) (<= end (+ offset (count subtext)))]}
  {:start offset
   :end (+ offset (count subtext))
   :children (->> [(subs subtext 0 start)
                   {:text (subs subtext start end) :format formatting-elm}
                   (subs subtext end)]
                  (remove empty?))})

(def ->range (juxt :start :end))

(defn apply-formatting [target {:keys [type url] :as formatting-elm}]
  (case type
    "bold"
    [:strong target]
    "italic"
    [:em target]
    "link"
    [:a {:href (unredirect url)} target]
    "small"
    [:span {:style "font-size: small"} target]))

(defn ->sparse-formatting-tree [text formatting]
  {:start 0
   :end (count text)
   ::children
   (->> formatting
        (sort-by (juxt :start :end) subranges-first-compare)
        (reduce
          (fn [prev-nodes {:keys [start end] :as current-fmt}]
            (let [[children prev-nodes']
                  (split-with #(subrange? (->range %) (->range current-fmt)) prev-nodes)]
              (conj prev-nodes'
                (merge current-fmt
                  (if (seq children)
                    {::children children}
                    {::text (subs text start end)})))))
          (list)))})

(defn apply-formatting-tree [text {:keys [start end ::children] :as formatting-tree}]
  (let [strings (map
                  (partial subs text)
                  (cons start (map :end children))
                  (concat (map :start children) [end]))]
    (rest
      (->>
       (interleave
         (->> children
              (map
                #(apply-formatting
                   (if (::children %)
                     (apply-formatting-tree text %)
                     (::text %))
                   %))
              (cons nil)) ; interleave needs same # elms; `rest` will drop it again
         strings)
       (remove #{""})))))

(defn format-text-content [text formatting]
  (->> formatting
       (->sparse-formatting-tree text)
       (apply-formatting-tree text)))

(comment
  (clojure.pprint/pprint ; tap>
    (format-text-content
      "UncleJim is a very interesting looking library, with these main features:"
      [{:type "bold", :start 0, :end 73} {:type "link", :start 0, :end 8, :url "https://href.li/?https://github.com/GlenKPeterson/UncleJim"}])))


(defn render-image-block [{:keys [summary]} {:keys [attribution media]}]
  (let [srcset (->> media
                     (map (fn [{:keys [url width]}] (str url " " width)))
                     (str/join ","))]
    [:img {:src (:url attribution)
           :srcset srcset
           :alt summary}]))

(defn render-link-block [{:keys [summary tags]} {:keys [url title description]}]
  {:pre [url summary]}
  (when (not= title summary)
    (println "WARN: link: (not= title summary)" title "!=" summary))
  [:span [:a {:href (unredirect url)} summary]
   " [" (str/join "," tags) "]"
   " - "
   description])

;; FIXME: Handle `:subtype "heading1"`
(defn render-text-block [_ {mytxt :text, :keys [formatting]}]
  (list [:br] (cond-> mytxt
                (seq formatting) (format-text-content formatting))))

(defn render-video-block [{:keys [summary]} 
                          {{:keys [display_text]} :attribution
                           :keys [embed_iframe embed_html url]}]
  {:pre [url]}
  (when (not= display_text summary)
    (println "WARN: video: (not= display_text summary)" display_text "!=" summary))
  [:span [:a {:href (unredirect url)} summary]])

(defn render-block [post {:keys [type] :as block}]
  (case type
    "image" (render-image-block post block)
    "link" (render-link-block post block)
    "text" (render-text-block post block)
    "video" (render-video-block post block)))

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
  (try
    [:p
     (doall (map #(render-block post %) content))]
    (catch Exception e
      (throw (ex-info
               (str "Failed to render post #" id ": " (ex-message e))
               {:post post, :err e}
               e)))))

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
  (def post (->> posts (filter #(= 130046336929 (:id %))) first))
  (->> post :content first (render-text-block nil))
  (render-post post)
  (doall (map #(render-block post %) (:content post)))
  
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
  video
  (keys video)

  (pr (render-post meander))
  (tap> (with-meta (render-post meander)
          {:portal.viewer/default :portal.viewer/hiccup}))
  
  (pr (mapv #(do
               (print "ID #" (:id %))
               (println (render-post %)))
        posts))
  
  (tap> (with-meta (render-post meander) 
          {:portal.viewer/default :portal.viewer/tree})))


#_(def sample (json/read-str
                (slurp "sample-post.json")))




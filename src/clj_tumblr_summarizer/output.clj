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
  (case type
    "bold"
    [:strong text]
    "italic"
    [:em text]
    "link"
    [:a {:href (unredirect (:url formatting))} text]
    "small" ; FIXME: impl
    text))

(defn format-text-content [text formatting]
  (->> (segment-text text formatting)
       (map #(cond->> %
               (vector? %) (apply apply-formatting)))))

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
  [:p
   (map #(render-block post %) content)])

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
  (def video (->> posts (filter #(= 189328582874 (:id %))) first))
  (-> video :content first tap>)
  
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
  
  (pr (with-meta [:div (map #(list [:hr]
                                 [:a {:href (:post_url %)} "src"]
                                 (render-post %)) 
                           posts)]
          {:portal.viewer/default :portal.viewer/hiccup}))
  
  (tap> (with-meta (render-post meander) 
          {:portal.viewer/default :portal.viewer/tree})))


  

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



;; TODO: Add to the index incrementally, only new stuff => faster; currently takes 3 min
(ns build-pagefind-index
  "Build a pagefind search index from .edn Tumblr entries previously downloaded
   into /data, together with the supporting .js, .css and other files.
   Serve statically ./search.html and ./pagefind/ to use the search."
  {:clj-kondo/ignore true}
  (:require
   ["fs" :as fs]
   ["node:fs/promises" :refer [readdir readFile]]
   ["url" :refer [fileURLToPath]]
   ["pagefind" :as pagefind]
   ["edn-data" :refer [parseEDNString]]))

(defn entry->record [{:keys [tags summary content timestamp post_url date] :as _entry}]
  (try
   (let [date (when date (.substring date 0 10))
         tags-str (-> tags (.sort) (.join ", "))
         content-str (-> (.map content (fn [{:keys [text description]}]
                                    ;; type 'link' -> description, text -> text
                                         (or text description)))
                         (.join "\n")
                         ;; Make pagefind index also the tags themselves:
                         (str "\n\nTags:" tags-str))
         type-str (some-> content first :type)
         typ (get {:text "📄"
                   :link "👓"
                   :video "🎥"} 
                  type-str type-str)]
     {:url post_url
      :content content-str
      :language "en"
     ;; optional:
      :meta (cond-> {:title summary
                     ;; These below will be displayed below the result
                     :date date #_image}
              (seq tags)
              (assoc! :tags tags-str)
              
              typ
              (assoc! :type typ))
      :filters {:tags tags}
      :sort {:date (some-> timestamp str)}})
    (catch :default err
      (println "ERROR processing entry" _entry ":" (.-message err))
      (throw err))))

(defn ^:async index-all []
  (let [entries (js-await (readdir "data"))
        index (.-index (js-await (pagefind/createIndex)))]
    ;; Add all entries to the index:
    (->
     (for [e entries
           :when (.endsWith e ".edn")]
       (-> (readFile (str "data/" e) {:encoding "utf8"})
           (.then #(parseEDNString % {:mapAs "object" :keywordAs "string"}))
           (.then entry->record)
           (.then #(.addCustomRecord index %))))
     (Promise/all)
     (js-await)) 
    ;; Save the index:
    (js-await (.writeFiles index {:outputPath "pagefind"}))
    (println "Index written with" (count entries) "entries")
    (js-await (.close pagefind))))

(js-await (index-all))
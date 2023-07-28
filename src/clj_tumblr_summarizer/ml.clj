(ns clj-tumblr-summarizer.ml
  (:require
   [clj-tumblr-summarizer.storage.fs :as fs]
   [clj-tumblr-summarizer.time :as time]
   [scicloj.ml.core :as ml]
   [scicloj.ml.metamorph :as mm]
   [scicloj.ml.dataset :as ds]))

@(def tag-lists-ds
   (-> (->> (fs/posts-in-time-range (time/previous-month))
            (map #(select-keys % [:tags])))
       (ds/dataset)
       (ds/separate-column :tags :infer #(zipmap % (repeat 1)))
       (ds/replace-missing :all :value 0)))

(def min-nr-neighbors 1) ; 1 => 4x, 2 => 2x cluster
(def radius 1) ; > 1 => single cluster

;; (def ds (-> [{:tags ["app" "mobile" "travel"]}
;;              {:tags ["business"]}
;;              {:tags ["clojure" "library" "data processing"]}
;;              {:tags ["clojure" "tool" "docker" "devops"]}
;;              {:tags ["tool" "automation" "macos"]}]
;;             (ds/dataset)
;;             (ds/separate-column :tags :infer #(zipmap % (repeat 1)))
;;             (ds/replace-missing :all :value 0)))

(def pipe-fn
  (ml/pipeline
    {:metamorph/id :tag-cluster}
    (scicloj.ml.metamorph/cluster :dbscan [min-nr-neighbors radius] :cluster-id)))

(->
 @(def trained-ctx
    (pipe-fn {:metamorph/data tag-lists-ds
              :metamorph/mode :fit}))
 
 (:metamorph/data trained-ctx)
 (ds/select-columns :cluster-id)
 (ds/unique-by :cluster-id))

;; (-> (ds/dataset tag-lists) (ds/fit-one-hot :tags))
;; (-> (ds/dataset tag-lists)
;;     ;(ds/add-column :id (range))
;;     (ds/separate-column :tags :infer #(zipmap % (repeat 1))))


;; EXAMPLE CODE
;; (def titanic-test
;;   (-> "https://github.com/scicloj/metamorph-examples/raw/main/data/titanic/test.csv"
;;       (ds/dataset {:key-fn keyword :parser-fn :string})
;;       (ds/add-column :Survived [""] :cycle)))

;; ;; construct pipeline function including Logistic Regression model
;; (def pipe-fn
;;   (ml/pipeline
;;     (mm/select-columns [:Survived :Pclass])
;;     (mm/add-column :Survived (fn [ds] (map #(case % "1" "yes" "0" "no" nil "") (:Survived ds))))
;;     (mm/categorical->number [:Survived :Pclass])
;;     (mm/set-inference-target :Survived)
;;     {:metamorph/id :model}
;;     (mm/model {:model-type :smile.classification/logistic-regression})))

;; ;;  execute pipeline with train data including model in mode :fit
;; (def trained-ctx
;;   (pipe-fn {:metamorph/data titanic-train
;;             :metamorph/mode :fit}))

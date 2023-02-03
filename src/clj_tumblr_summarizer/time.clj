(ns clj-tumblr-summarizer.time
  "Time utilities - for deriving time ranges of posts."
  (:require [clojure.instant :as instant])
  (:import
   (java.time Instant LocalDate ZonedDateTime ZoneId)
   (java.time.format TextStyle)
   (java.time.temporal TemporalAdjusters ChronoUnit)
   (java.util Locale)))

(defn ld->epoch-sec [^LocalDate ld]
  (.toEpochSecond (.atStartOfDay ld (ZoneId/of "UTC"))))

(defn date-str->ld 
  "Clojure-parseable date string such as '2023-01' to LocalDate"
  [clj-inst-date-str]
  (-> (.toInstant (instant/read-instant-date clj-inst-date-str))
      (.atZone (ZoneId/systemDefault))
      (.toLocalDate)))

(defn month-period [^LocalDate any-month-date]
  (let [target-month-start (.with any-month-date (TemporalAdjusters/firstDayOfMonth))
        next-month-start
        (.plus target-month-start 1 ChronoUnit/MONTHS)]
    (mapv ld->epoch-sec
          [target-month-start
           next-month-start])))

(defn any-month [^String any-month-date]
  (-> any-month-date date-str->ld month-period))

(defn previous-month
  "Returns range for the previous month in epoch seconds (UTC) with 
   start inclusive, end exclusive"
  []
  (month-period (.minus (LocalDate/now) 1 ChronoUnit/MONTHS)))

(defn current-month-so-far
  "Epoch ts range [start, end) for the current month."
  []
  (let [current-month-start
        (.with (LocalDate/now) (TemporalAdjusters/firstDayOfMonth))

        current-month-end
        (.plus current-month-start 1 ChronoUnit/MONTHS)]
    (mapv ld->epoch-sec [current-month-start current-month-end])))

(defn epoch-ts->zdt ^ZonedDateTime [ts]
  (-> (Instant/ofEpochSecond ts)
      (ZonedDateTime/ofInstant (ZoneId/of "UTC"))))

(defn zdt->month [^ZonedDateTime zdt]
  ; TextStyle/SHORT -> "Jan"
  (-> zdt .getMonth (.getDisplayName TextStyle/FULL Locale/ENGLISH)))

(defn zdt->year [^ZonedDateTime zdt]
  (.getYear zdt))

(comment
  (str (epoch-ts->zdt 1615633012)))
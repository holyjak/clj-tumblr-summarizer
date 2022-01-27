(ns clj-tumblr-summarizer.time
  "Time utilities - for deriving time ranges of posts."
  (:import 
    (java.time LocalDate ZoneId)
    (java.time.temporal TemporalAdjusters ChronoUnit)))

(defn ld->epoch-sec [^LocalDate ld]
  (.toEpochSecond (.atStartOfDay ld (ZoneId/of "UTC"))))

(defn previous-month-epoch-secs 
  "Returns range for the previous month in epoch seconds (UTC) with 
   start inclusive, end exclusive"
  []
  (let [current-month-start
        (.with (LocalDate/now) (TemporalAdjusters/firstDayOfMonth))

        prev-month-start
        (.minus current-month-start 1 ChronoUnit/MONTHS)]
    (mapv ld->epoch-sec
      [prev-month-start
       current-month-start])))

  (comment 
    (previous-month-epoch-secs))

#_
(-> (java.time.Instant/ofEpochSecond 1642806217)
    (java.time.ZonedDateTime/ofInstant (ZoneId/of "UTC")))
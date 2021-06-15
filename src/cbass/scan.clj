(ns cbass.scan
  (:require [cbass.tools :refer [to-bytes from-bytes]])
  (:import [org.apache.hadoop.hbase.filter Filter KeyOnlyFilter FirstKeyOnlyFilter FilterList FilterList$Operator]
           [org.apache.hadoop.hbase.client Scan]))

(defn- set-start-row! [^Scan scanner ^String prefix]
  (.setStartRow scanner (to-bytes prefix)))

(defn- set-stop-row! [^Scan scanner ^String prefix]
  (.setStopRow scanner (to-bytes prefix)))

;; (!) ".setRowPrefixFilter" is only available since "hbase client 0.99.1"
(defn- set-row-prefix! [^Scan scanner ^String prefix]
  (when prefix
    (.setRowPrefixFilter scanner (to-bytes prefix))))

(defn- set-time-range! [^Scan scanner [from to]]
  (let [f (or from 0)
        t (or to Long/MAX_VALUE)]
    (.setTimeRange scanner f t)))

(defn set-filter! [^Scan scanner ^Filter f]
  (when f
    (.setFilter scanner f)))

(defn- set-reverse! [^Scan scanner reverse?]
  (.setReversed scanner reverse?))

(defn- set-caching! [^Scan scanner limit]
  (.setCaching scanner limit))

(defn- add-family [^Scan scanner ^String  family]
  (.addFamily scanner (to-bytes family)))

(defn- add-columns [^Scan scanner [^String family columns]]
  (doseq [^String c columns]
    (.addColumn scanner (to-bytes family) (to-bytes (name c)))))

(defn- get-keys-only-filter []
  (doto (FilterList. FilterList$Operator/MUST_PASS_ALL)
    (.addFilter (KeyOnlyFilter.))
    (.addFilter (FirstKeyOnlyFilter.))))

;; doing one family many columns for now
(defn scan-filter [{:keys [keys-only? filter family columns starts-with from to time-range reverse? fetch-size]}]
  (when (and keys-only? filter)
    (throw (ex-info "filter & keys-only?=true cannot be combined" [keys-only? filter])))
  (let [scanner (Scan.)
        filter (if keys-only? (get-keys-only-filter) filter)
        {:keys [from-ms to-ms]} time-range
        params [(if (and family (seq columns))
                  [add-columns [family columns]]
                  [add-family family])
                [set-filter! filter]
                [set-reverse! reverse?]
                [set-caching! fetch-size]
                [set-row-prefix! starts-with]
                [set-start-row! from]
                [set-stop-row! to]
                [set-time-range! (when (or from-ms to-ms) 
                                   [from-ms to-ms])]]]
    (doall (map (fn [[f p]] 
                  (when p (f scanner p))) params))
    scanner))

(ns cbass
  (:require [taoensso.nippy :as n]
            [cbass.scan :refer [scan-filter]]
            [cbass.tools :refer [to-bytes thaw]])
  (:import [java.util ArrayList]
           [org.apache.hadoop.hbase.util Bytes]
           [org.apache.hadoop.hbase TableName HConstants]
           [org.apache.hadoop.conf Configuration]
           [org.apache.hadoop.hbase.client HConnection HConnectionManager HTableInterface Get Put Delete Scan Result]))

(def pack (atom n/freeze))
(def unpack (atom thaw))

(defn pack-un-pack [{:keys [p u]}]
  (when p (reset! pack p))
  (when u (reset! unpack u)))

(defn- ^HConnection create-connection [conf]
  (let [configuration (Configuration.)]
    (doseq [[k v] conf]
      (.set configuration k (str v)))
    (HConnectionManager/createConnection configuration)))

(def new-connection 
  (memoize create-connection))

(defn get-table [^HConnection c ^String t-name]
  (.getTable c t-name))

(defn result-key [rk] 
  (-> (String. (key rk)) keyword))

(defn result-value [rv] 
  (@unpack (val rv)))

(defn hdata->map [^Result data]
  (when-let [r (.getRow data)]
    (into {} (for [kv (-> (.getNoVersionMap data) vals first)] 
               (if-some [v (result-value kv)]
                 [(result-key kv) v])))))

(defn map->hdata [row-key family columns]
  (let [^Put p (Put. (to-bytes row-key))
        f-bytes (to-bytes family)]
    (doseq [[k v] columns]
      (when-let [v-bytes (@pack v)]
        (.add p f-bytes (to-bytes (name k)) v-bytes)))
    p))

(defn results->map [results row-key-fn]
  (into {} (for [r results] 
             [(row-key-fn (.getRow r)) 
              (hdata->map r)])))

(defn scan [conn table & {:keys [row-key-fn] :as criteria}]
  (with-open [^HTableInterface h-table (get-table conn table)]
    (let [results (-> (.iterator (.getScanner h-table (scan-filter criteria)))
                      iterator-seq)
          row-key-fn (or row-key-fn #(String. %))]
      (results->map results row-key-fn))))

(defn find-by
  ([conn table row-key]
   (find-by conn table row-key nil nil))
  ([conn table row-key family]
   (find-by conn table row-key family nil))
  ([conn table row-key family columns]
    (with-open [^HTableInterface h-table (get-table conn table)]
      (let [^Get g (Get. (to-bytes row-key))]
        (when family
          (if columns
            (doseq [c columns] 
              (.addColumn g (to-bytes family) (to-bytes (name c))))
            (.addFamily g (to-bytes family))))
        (-> (.get h-table g)
            hdata->map)))))

(defn store 
  ([conn table row-key family]
    (with-open [^HTableInterface h-table (get-table conn table)]
      (let [^Put p (Put. (to-bytes row-key))]
        (.put h-table (.add p (to-bytes family)   ;; in case there are no columns, just store row-key and family
                              (byte-array 0) 
                              (byte-array 0))))))
  ([conn table row-key family columns]
    (with-open [^HTableInterface h-table (get-table conn table)]
      (let [^Put h-data (map->hdata row-key family columns)]
        (.put h-table h-data)))))

(defn delete
  ([conn table row-key]
   (delete conn table row-key nil nil))
  ([conn table row-key family]
   (delete conn table row-key family nil))
  ([conn table row-key family columns]
    (with-open [^HTableInterface h-table (get-table conn table)]
      (let [^Delete d (Delete. (to-bytes row-key))]
        (when family
          (if columns
            (doseq [c columns] 
              (.deleteColumns d (to-bytes family) (to-bytes (name c))))
            (.deleteFamily d (to-bytes family))))
        (.delete h-table d)))))

(defn delete-by [conn table & by]
  (let [row-keys (keys (apply scan conn table by))
        delete-key-fn (:delete-key-fn (apply hash-map by))
        bulk (ArrayList. (map #(Delete. (if delete-key-fn
                                          (delete-key-fn %)
                                          (to-bytes %))) row-keys))]
    (when (seq row-keys)
      (with-open [^HTableInterface h-table (get-table conn table)]
        (.delete h-table bulk)))))

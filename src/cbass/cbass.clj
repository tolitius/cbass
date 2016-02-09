(ns cbass
  (:require [taoensso.nippy :as n]
            [cbass.scan :refer [scan-filter]]
            [cbass.tools :refer [to-bytes thaw no-values]])
  (:import [java.util ArrayList]
           [org.apache.hadoop.hbase.util Bytes]
           [org.apache.hadoop.hbase TableName]
           [org.apache.hadoop.conf Configuration]
           [org.apache.hadoop.hbase.client HConnection HConnectionManager HTableInterface Get Put Delete Scan Result]))

(def pack (atom n/freeze))
(def unpack (atom thaw))

(defn pack-un-pack [{:keys [p u]}]
  (when p (reset! pack p))
  (when u (reset! unpack u)))

(defn ^HConnection new-connection [conf & {:keys [pack unpack]}]
  (let [configuration (Configuration.)]
    (doseq [[k v] conf]
      (.set configuration k (str v)))
    (pack-un-pack {:p pack :u unpack})
    (HConnectionManager/createConnection configuration)))

(defn get-table [^HConnection c ^String t-name]
  (.getTable c t-name))

(defn result-key [rk]
  (-> (String. (key rk)) keyword))

(defn result-value [rv]
  (@unpack (val rv)))

(defn latest-ts [^Result result]
  (let [cells (.rawCells result)]
    (->> (map #(.getTimestamp %) cells)
         (apply max))))

(defn hdata->map [^Result data]
  (when-let [r (.getRow data)]
    (let [ts (latest-ts data)
          results (for [kv (-> (.getNoVersionMap data) vals first)]
                    (if-some [v (result-value kv)]
                      [(result-key kv) v]))]
      (into {:last-updated ts} results))))

(defn map->hdata [row-key family columns]
  (let [^Put p (Put. (to-bytes row-key))
        f-bytes (to-bytes family)]
    (doseq [[k v] columns]
      (when-let [v-bytes (@pack v)]
        (.add p f-bytes (to-bytes (name k)) v-bytes)))
    p))

(defn results->maps [results row-key-fn]
  (for [r results]
    [(row-key-fn (.getRow r))
     (hdata->map r)]))

(defn without-ts [results]
  (for [[k v] results]
    [k (dissoc v :last-updated)]))

(defn scan [conn table & {:keys [row-key-fn limit with-ts? lazy?] :as criteria}]
  (with-open [^HTableInterface h-table (get-table conn table)]
    (let [results (-> (.iterator (.getScanner h-table (scan-filter criteria)))
                      iterator-seq)
          row-key-fn (or row-key-fn #(String. %))
          rmap (results->maps (if-not limit
                               results
                               (take limit results))
                             row-key-fn)]
      (cond->> rmap
        (not with-ts?) (without-ts)
        (not lazy?) (into {})))))

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
                              no-values
                              no-values)))))
  ([conn table row-key family columns]
    (with-open [^HTableInterface h-table (get-table conn table)]
      (let [^Put h-data (map->hdata row-key family columns)]
        (.put h-table h-data)))))

(defn empty-row-put [row-key family]
  (let [^Put p (Put. (to-bytes row-key))]
    (.add p (to-bytes family)
          no-values
          no-values)
    p))

(defn store-batch [conn table rows]
  (with-open [^HTableInterface h-table (get-table conn table)]
    (let [bulk (ArrayList. (for [[r f cs] rows]
                             (if cs
                               (map->hdata r f cs)
                               (empty-row-put r f))))]

      (.put h-table bulk))))

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
  "Delete by using scan's syntax for the selection criteria."
  (let [row-keys (->>
                  (conj by true :lazy?) ;; always fetch keys lazily to avoid OOM errors
                  (apply scan conn table)
                  (map first))
        delete-key-fn (:delete-key-fn (apply hash-map by))
        bulk (ArrayList. (map #(Delete. (if delete-key-fn
                                          (delete-key-fn %)
                                          (to-bytes %))) row-keys))]
    (when (seq row-keys)
      (with-open [^HTableInterface h-table (get-table conn table)]
        (.delete h-table bulk)))))

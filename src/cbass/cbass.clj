(ns cbass
  (:require [taoensso.nippy :as n]
            [cbass.scan :refer [scan-filter]]
            [cbass.tools :refer [to-bytes thaw no-values]])
  (:import [java.util ArrayList Collection List]
           [org.apache.hadoop.hbase.util Bytes]
           [org.apache.hadoop.hbase TableName Cell]
           [org.apache.hadoop.conf Configuration]
           [org.apache.hadoop.hbase.client Connection ConnectionFactory Table Get Put Delete Scan Result ResultScanner]))

(def pack (atom n/freeze))
(def unpack (atom thaw))

(defn pack-un-pack [{:keys [p u]}]
  (when p (reset! pack p))
  (when u (reset! unpack u)))

(defn ^Connection new-connection [conf & {:keys [pack unpack]}]
  (let [configuration (Configuration.)]
    (doseq [[k v] conf]
      (.set configuration k (str v)))
    (pack-un-pack {:p pack :u unpack})
    (ConnectionFactory/createConnection configuration)))

(defn ^Table get-table [^Connection c ^String t-name]
  (.getTable c (TableName/valueOf t-name)))

(defn result-key [rk]
  (-> (String. ^bytes (key rk)) keyword))

(defn result-value [rv]
  (@unpack (val rv)))

(defn latest-ts [^Result result]
  (let [cells (.rawCells result)]
    (->> (map #(.getTimestamp ^Cell %) cells)
         (apply max))))

(defn hdata->map [^Result data]
  (when-let [r (.getRow data)]
    (let [ts (latest-ts data)
          results (for [kv (-> (.getNoVersionMap data) vals first)]
                    (if-some [v (result-value kv)]
                      [(result-key kv) v]))]
      (into {:last-updated ts} results))))

(defn map->hdata [row-key family columns]
  (let [^Put p (Put. ^bytes (to-bytes ^String row-key))
        ^bytes f-bytes (to-bytes ^String family)]
    (doseq [[k v] columns]
      (when-let [v-bytes (@pack v)]
        (.add p f-bytes (to-bytes (name k)) v-bytes)))
    p))

(defn results->maps [results row-key-fn]
  (for [^Result r results]
    [(row-key-fn (.getRow r))
     (hdata->map r)]))

(defn without-ts [results]
  (for [[k v] results]
    [k (dissoc v :last-updated)]))

(defn scan [conn table & {:keys [row-key-fn limit with-ts? lazy?] :as criteria}]
  (let [scan ^Scan (scan-filter criteria)]
    (when limit
      (.setLimit scan limit))
    (with-open [^Table h-table (get-table conn table)
                scanner (.getScanner h-table scan)]
      (let [results (-> (.iterator scanner)
                        iterator-seq)
            row-key-fn (or row-key-fn #(String. ^bytes %))
            rmap (results->maps results
                                row-key-fn)]
        (cond->> rmap
                 (not with-ts?) (without-ts)
                 (not lazy?) (into {}))))))

(defn lazy-scan
  "
  Scan rows. Return a map with:

  - table: the table object
  - scanner: the scanner object
  - rows: A lazy sequence with the rows

  IMPORTANT: It's the responsibility of the caller to close table and scanner."
  [conn table & {:keys [row-key-fn limit with-ts?] :as criteria}]
  (let [scan ^Scan (scan-filter criteria)]
    (when limit
      (.setLimit scan limit))
    (let [^Table h-table         (get-table conn table)
          ^ResultScanner scanner (.getScanner h-table scan)
          results                (-> (.iterator scanner)
                                     iterator-seq)
          row-key-fn             (or row-key-fn #(String. ^bytes %))
          rmap                   (results->maps results
                                                row-key-fn)]

      {:table   h-table
       :scanner scanner
       :rows    (cond->> rmap
                  (not with-ts?) (without-ts))})))

(defn find-by
  ([conn table row-key]
   (find-by conn table row-key nil nil))
  ([conn table row-key family]
   (find-by conn table row-key family nil))
  ([conn table row-key family columns]
    (with-open [^Table h-table (get-table conn table)]
      (let [^Get g (Get. ^bytes (to-bytes ^String row-key))]
        (when family
          (if columns
            (doseq [c columns]
              (.addColumn g (to-bytes ^String family) (to-bytes (name c))))
            (.addFamily g (to-bytes ^String family))))
        (-> (.get h-table g)
            hdata->map)))))

(defn store
  ([conn table row-key family]
    (with-open [^Table h-table (get-table conn table)]
      (let [^Put p (Put. ^bytes (to-bytes ^String row-key))]
        (.put h-table (.add p (to-bytes  ^String family)   ;; in case there are no columns, just store row-key and family
                              no-values
                              no-values)))))
  ([conn table row-key family columns]
    (with-open [^Table h-table (get-table conn table)]
      (let [^Put h-data (map->hdata row-key family columns)]
        (.put h-table h-data)))))

(defn empty-row-put [row-key family]
  (let [^Put p (Put. ^bytes (to-bytes ^String row-key))]
    (.add p (to-bytes ^String family)
          no-values
          no-values)
    p))

(defn store-batch [conn table rows]
  (with-open [^Table h-table (get-table conn table)]
    (let [bulk (doall (for [[r f cs] rows]
                             (if cs
                               (map->hdata r f cs)
                               (empty-row-put r f))))]

      (.put h-table ^List bulk))))

(defn delete
  ([conn table row-key]
   (delete conn table row-key nil nil))
  ([conn table row-key family]
   (delete conn table row-key family nil))
  ([conn table row-key family columns]
    (with-open [^Table h-table (get-table conn table)]
      (let [^Delete d (Delete. ^bytes (to-bytes ^String row-key))]
        (when family
          (if columns
            (doseq [c columns]
              (.deleteColumns d (to-bytes ^String family) (to-bytes (name c))))
            (.deleteFamily d (to-bytes ^String family))))
        (.delete h-table d)))))

(defn delete-by [conn table & by]
  "Delete by using scan's syntax for the selection criteria."
  (let [row-keys (->>
                  (conj by true :lazy?) ;; always fetch keys lazily to avoid OOM errors
                  (apply scan conn table)
                  (map first))
        delete-key-fn (:delete-key-fn (apply hash-map by))
        bulk (ArrayList. ^Collection (map #(Delete. ^bytes (if delete-key-fn
                                          (delete-key-fn %)
                                          (to-bytes ^String %))) row-keys))]
    (when (seq row-keys)
      (with-open [^Table h-table (get-table conn table)]
        (.delete h-table ^ArrayList bulk)))))

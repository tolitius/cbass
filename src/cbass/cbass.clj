(ns cbass
  (:require [taoensso.nippy :as n])
  (:import [org.apache.hadoop.hbase.util Bytes]
           [org.apache.hadoop.hbase TableName HConstants]
           [org.apache.hadoop.conf Configuration]
           [org.apache.hadoop.hbase.client HConnection HConnectionManager HTableInterface Get Put Delete Scan Result]))

(defmacro sbytes [s]
 `(.getBytes ~s))

(defmacro thaw [data]
  `(when ~data 
     (n/thaw ~data)))

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
  (thaw (val rv)))

(defn hget [row-key]
  (Get. (sbytes (str row-key))))

(defn get-with-keys [ks row-key cf]
  (let [^Get g (hget row-key)
        cf-bytes (sbytes cf)]
    (doseq [k ks]
      (.addColumn g cf-bytes (sbytes (name k))))
    g))

(defn hdata->map [^Result data]
  (into {} (for [kv (-> (.getNoVersionMap data) vals first)] 
             [(result-key kv) (result-value kv)])))

(defn map->hdata [m row-key cf]
  (let [^Put p (Put. (sbytes (str row-key)))
        cf-bytes (sbytes cf)]
    (doseq [[k v] m]
      (when-let [v-bytes (n/freeze v)]
        (.add p cf-bytes (sbytes (name k)) v-bytes)))
    p))

(defn find-in [conn table row-key]
  (with-open [^HTableInterface h-table (get-table conn table)]
    (let [^Get g (hget row-key)]
      (-> (.get h-table g)
          hdata->map))))

(defn store [conn table row-key cf vs]
  (with-open [^HTableInterface h-table (get-table conn table)]
    (let [^Put h-data (map->hdata vs row-key cf)]
      (.put h-table h-data))))

(defn delete
  ([conn table row-key]
   (delete conn table row-key nil nil))
  ([conn table row-key family]
   (delete conn table row-key family nil))
  ([conn table row-key family columns]
    (with-open [^HTableInterface h-table (get-table conn table)]
      (let [^Delete d (Delete. (sbytes (str row-key)))]
        (when family
          (if columns
            (doseq [c columns] 
              (.deleteColumns d (sbytes (str family)) (sbytes (name c))))
            (.deleteFamily d (sbytes (str family)))))
        (.delete h-table d)))))

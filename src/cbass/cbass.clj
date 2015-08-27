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

(defn hget [rkey]
  (Get. (sbytes (str rkey))))

(defn get-with-keys [ks rkey cf]
  (let [^Get g (hget rkey)
        cf-bytes (sbytes cf)]
    (doseq [k ks]
      (.addColumn g cf-bytes (sbytes (name k))))
    g))

(defn hdata->map [^Result data]
  (into {} (for [kv (-> (.getNoVersionMap data) vals first)] 
             [(result-key kv) (result-value kv)])))

(defn map->hdata [m rkey cf]
  (let [^Put p (Put. (sbytes (str rkey)))
        cf-bytes (sbytes cf)]
    (doseq [[k v] m]
      (when-let [v-bytes (n/freeze v)]
        (.add p cf-bytes (sbytes (name k)) v-bytes)))
    p))

(defn find-in [conn table row-key]
  (let [^Get g (hget row-key)
        ^HTableInterface h-table (get-table conn table)]
    (-> (.get h-table g)
        hdata->map)))

(defn store-in [conn table rkey cf vs]
  (let [^HTableInterface h-table (get-table conn table)
        ^Put h-data (map->hdata vs rkey cf)]
    (.put h-table h-data)
    (.close h-table)))

;; (def conf {"hbase.zookeeper.quorum" "127.0.0.1:2181" "zookeeper.session.timeout" 30000})
;; (require '[cbass :refer [new-connection store-in find-in]])
;; (def conn (new-connection conf))
;; 
;; (store-in conn "galaxy:planet" 42 "galaxy" {:inhabited? true :population 7125000000 :age "4.543 billion years"})
;; (find-in conn "galaxy:planet" 42)

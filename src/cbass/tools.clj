(ns cbass.tools
  (:require [taoensso.nippy :as n]
            [æsahættr :refer [hash-object hash-bytes murmur3-32 murmur3-128]])
  (:import [org.apache.hadoop.hbase.util Bytes]
           [java.time Instant ZoneId ZonedDateTime ZoneOffset Duration]
           [java.time.format DateTimeFormatter]
           (com.google.common.hash HashCode)))

(defmacro bytes? [s]
  `(= (Class/forName "[B")
      (.getClass ~s)))

(defmacro to-bytes [s]
 `(if-not (bytes? ~s)
    (Bytes/toBytes ~s)
    ~s))

(defmacro from-bytes [s]
 `(Bytes/fromBytes ~s))

(defn thaw [data]
  (when (seq data)
    (n/thaw data)))

(defonce no-values
  (byte-array 0))

(defn hash-it [obj]
  (->> obj
       (hash-object (murmur3-128))
       str))

(defn hash-key [#^bytes k-bytes]
  (.asBytes ^HashCode (hash-bytes (murmur3-32)
                                  k-bytes)))

(defn current-utc-millis []
  (-> (ZoneOffset/UTC)
      (ZonedDateTime/now)
      (.toInstant)
      (.toEpochMilli)))

(defn parse-long [ts]
  (try
    (Long/valueOf ^String ts)
    (catch Throwable t
      (prn "could not parse " ts " to a Long due to " (class t) ": " (.getMessage t)))))

(defn str-now [ts]
  (if-let [timestamp (parse-long ts)]
    (-> (ZonedDateTime/ofInstant
          (Instant/ofEpochMilli timestamp)
          (ZoneId/of "UTC"))
        (.format (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSS")))))

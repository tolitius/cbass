(ns cbass.tools
  (:require [taoensso.nippy :as n])
  (:import [org.apache.hadoop.hbase.util Bytes]))

(defmacro bytes? [s]
  `(= (Class/forName "[B")
      (.getClass ~s)))

(defmacro to-bytes [s]
 `(if-not (bytes? ~s)
    (Bytes/toBytes ~s)
    ~s))

(defmacro from-bytes [s]
 `(Bytes/fromBytes ~s))

(defmacro thaw [data]
  `(when (seq ~data)
     (n/thaw ~data)))


(ns cbass.tools
  (:require [taoensso.nippy :as n])
  (:import [org.apache.hadoop.hbase.util Bytes]))

(defmacro to-bytes [s]
 `(Bytes/toBytes ~s))

(defmacro from-bytes [s]
 `(Bytes/fromBytes ~s))

(defmacro thaw [data]
  `(when ~data 
     (n/thaw ~data)))


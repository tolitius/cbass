(ns cbass.mutate
  "Helper methods to allow for mutations https://hbase.apache.org/1.2/apidocs/org/apache/hadoop/hbase/client/Mutation.html
  Right now it only supports increment."
  (:require [cbass.tools :refer [to-bytes]]
            [cbass :as cbass])
  (:import [org.apache.hadoop.hbase.client Table Increment]))

(defn ^Increment get-increment-op
  "This method creates insert mutation calls for hbase.
  - family can be a string or a keyword
  - row-key should be a string
  - columns may be specified as either a keyword or a string

  This can be called with either a single column and amount or a list of pairs to set

  See tests_mutate.clj for examples.
  "
  ([row-key family column amount]
   (get-increment-op row-key family [[column amount]]))
  ([row-key family columns_and_amounts]
   (let [^Increment i (Increment. #^bytes (to-bytes row-key))]
     (doseq [[col amt] columns_and_amounts]
       (.addColumn i (to-bytes (name family)) (to-bytes (name col)) amt))
     i)))

(defn increment
  "A single increment operation"
  ([conn table row-key family column amount]
   (increment conn table row-key family [[column amount]]))
  ([conn table row-key family columns_and_amounts]
   (with-open [^Table h-table (cbass/get-table conn table)]
     (.increment h-table (get-increment-op row-key family columns_and_amounts)))))

(defn increment-batch
  "Process increments in batch.
   Increments is a vector obtained by calls to get-increment-op"
  [conn table increments]
  (let [results (object-array (count increments))]
    (with-open [^Table h-table (cbass/get-table conn table)]
      (.batch h-table increments results))
    results))
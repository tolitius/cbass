(ns cbass.keymaker
  (:require [cbass.tools :refer [to-bytes hash-key current-utc-millis str-now]]
            [clojure.string :as s]))

(defonce | (.getBytes "|"))
(defonce |+ (.getBytes "}"))
(defonce hash-and-pipe 5)     ;; murmur32 is 4 bytes + 1 for a pipe

(defonce splitter 
  (re-pattern (str "\\" (String. |))))

(defn split-key [k]
  (s/split k splitter))

(defn seq->byte-array [xs]
  (byte-array (mapcat seq xs)))

(defn without-hash [k]
  (->> k 
       (drop hash-and-pipe) 
       byte-array 
       (String.)))

(defn key-nth [k n]
  (-> k
      without-hash
      split-key
      (nth n)))

(defn with-hash-pipe [^String k to]
  (let [#^bytes k-bytes (.getBytes (s/trim k))
        #^bytes prefix (hash-key k-bytes)]
    (seq->byte-array [prefix | k-bytes to])))

(defn hash-pipe [^String k]
  (with-hash-pipe k |))

(defn hash-pipe+ [^String k]
  (with-hash-pipe k |+))

(defn with-hash 
  ([^String k]
    (let [#^bytes k-bytes (.getBytes (s/trim k))
          #^bytes prefix (hash-key k-bytes)]
      (seq->byte-array [prefix | k-bytes])))

  ([^String from ^String to]
  (let [#^bytes f-bytes (.getBytes from)
        #^bytes t-bytes (.getBytes to)
        #^bytes prefix (hash-key f-bytes)]
    (seq->byte-array [prefix | f-bytes | t-bytes]))))

(defn with-hash-and-ts 
  ([^String k]
   (with-hash-and-ts k (current-utc-millis)))
  ([^String k ts]
    (let [#^bytes k-bytes (.getBytes (s/trim k))
          #^bytes prefix (hash-key k-bytes)
          #^bytes ts (to-bytes (str ts))]           ;; TODO: should be no "str" if timestamp is just long
      (seq->byte-array [prefix | k-bytes | ts]))))

(defn key->ts [k]
  (-> k split-key last))

(defn key->date [k]
  (-> k key->ts str-now))

(defn keys->dates [m]
  (into []
    (for [[k v] m]
      [(key->date k) v])))

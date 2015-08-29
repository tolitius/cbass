(ns cbass.test
  (:require [clojure.test :refer :all]
            [cbass :refer :all]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))

(comment

(def s (scan-filter {:family "galaxy"
                     :from 42 :to "47"
                     :time-range {:from-ms 123 :to-ms 890}}))

[ (apply str (.getStartRow s))
  (apply str (.getStopRow s))
  (String. (first (.getFamilies s)))
  (String. (ffirst (vals (.getFamilyMap s))))
  (.getTimeRange s) ]

)

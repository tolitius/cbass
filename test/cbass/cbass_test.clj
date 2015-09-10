(ns cbass.test
  (:require [clojure.test :refer :all]
            [cbass :refer :all]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))

(defn stage-solar [conn]
  (store conn "galaxy:planet" "mars" "galaxy" {:inhabited? true :population 3 :age "4.503 billion years"})
  (store conn "galaxy:planet" "earth" "galaxy" {:inhabited? true :population 7125000000 :age "4.543 billion years"})
  (store conn "galaxy:planet" "pluto" "galaxy")
  (store conn "galaxy:planet" "saturn" "galaxy" {:inhabited? :unknown :age "4.503 billion years"})
  (store conn "galaxy:planet" "saturday" "galaxy" {:inhabited? :sometimes :age "24 hours"})
  (store conn "galaxy:planet" "neptune" "galaxy" {:inhabited? :unknown :age "4.503 billion years"}))

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

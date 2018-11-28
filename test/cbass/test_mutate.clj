(ns cbass.test_mutate
  (:require [clojure.test :refer :all]
            [cbass.tools :refer [to-bytes bytes->num bytes->str]]
            [cbass.mutate :refer :all]))

;; test for increments

(def d1 {:table "foo"
         :cf    "cf1"
         :key   "ABC123"
         :data  [[:x 1] [:y 2] [:z 4]]})

(deftest test-single-increment
  (testing "Get increment op"
    (let [op (get-increment-op (:key d1) (:cf d1) :x 1)]
      (is (= 1 (.numFamilies op)))
      (is (= (:key d1) (bytes->str (.getRow op))))
      (is (= "x"
             (-> op
                 (.getFamilyMap)
                 (.get (to-bytes (:cf d1)))
                 first
                 (.getQualifier)
                 bytes->str
                 )))
      (is (= 1
             (-> op
                 (.getFamilyMap)
                 (.get (to-bytes (:cf d1)))
                 first
                 (.getValue)
                 bytes->num
                 ))))))

(deftest test-multiple-increments
  (testing "increments"
    (let [ops (get-increment-op (:key d1) (:cf d1) (:data d1))]
      (= "y"
         (-> ops
             (.getFamilyMap)
             (.get (to-bytes (:cf d1)))
             second
             (.getQualifier)
             bytes->str))
      (= 3 (-> ops (.getFamilyMap) (.get (to-bytes (:cf d1))) count)))))


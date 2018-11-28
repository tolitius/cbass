(ns cbass.test
  (:require [clojure.test :refer :all]
            [cbass :refer :all]))

(defn connect []
  (new-connection {"hbase.zookeeper.quorum"
                   "localhost:2181"
                   "zookeeper.session.timeout"
                   30000}))

(defn create-solar [conn]
  (store-batch conn "galaxy:planet"
               [["mars" "galaxy" {:inhabited? true :population 3 :age "4.503 billion years"}]
                ["earth" "galaxy" {:inhabited? true :population 7125000000 :age "4.543 billion years"}]
                ["pluto" "galaxy"]
                ["saturn" "galaxy" {:inhabited? :unknown :age "4.503 billion years"}]
                ["saturday" "galaxy" {:inhabited? :sometimes :age "24 hours"}]
                ["neptune" "galaxy" {:inhabited? :unknown :age "4.503 billion years"}]]))

(deftest test-delete-by-lazy
  (with-redefs [scan (fn [_ _ & {:keys [lazy?] :as by}]
                       (when-not lazy? (is false "Scan is not lazy"))
                       {})]
    (is (nil? (delete-by (connect) "galaxy:planet")))
    (is (nil? (delete-by (connect) "galaxy:planet" :filter nil)))))

;; hbase(main):010:0* create_namespace 'galaxy';
;; hbase(main):011:0* create 'galaxy:planet', 'galaxy';

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
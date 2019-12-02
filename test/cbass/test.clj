(ns cbass.test
  (:require [clojure.test :refer :all]
            [cbass :refer :all]))

(defn connect []
  ; for an integration test ... (new-connection {...}))
  {"hbase.zookeeper.quorum"
   "localhost:2181"
   "zookeeper.session.timeout"
   30000})

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
                       {})
                with-open (fn [])
                get-table (fn [])]
    (is (nil? (delete-by (connect) "galaxy:planet")))
    (is (nil? (delete-by (connect) "galaxy:planet" :filter nil)))))

;; hbase(main):010:0* create_namespace 'galaxy';
;; hbase(main):011:0* create 'galaxy:planet', 'galaxy';

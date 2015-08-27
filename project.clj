(defproject cbass "0.1.0-SNAPSHOT"
  :description "adding simple to HBase"
  :url "https://github.com/tolitius/cbass"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src" "src/cbass"]
  :java-source-paths ["src/java"]

  :dependencies [[org.apache.hbase/hbase-client "0.98.6-cdh5.2.1" :exclusions [com.google.guava/guava]]
                 ;;[org.apache.hbase/hbase-shaded-client "1.1.1"]
                 [com.google.guava/guava "18.0"]
                 [com.taoensso/nippy "2.9.0"]
                 [org.clojure/clojure "1.7.0"]]

  :repositories {"cloudera" 
                 {:url "https://repository.cloudera.com/artifactory/cloudera-repos"}})


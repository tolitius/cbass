(defproject cbass "0.2.1"
  :description "adding simple to HBase"
  :url "https://github.com/tolitius/cbass"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src" "src/cbass"]

  :dependencies [[org.apache.hbase/hbase-client "1.2.6"]
                 [aesahaettr "0.1.2" :exclusions [com.google.guava/guava]]
                 [com.taoensso/nippy "2.13.0"]
                 [org.clojure/clojure "1.8.0"]]
  :global-vars {*warn-on-reflection* true}
  :aot :all
  :repositories {"cloudera"
                 {:url "https://repository.cloudera.com/artifactory/cloudera-repos"}})

(defproject exoscale/seql "0.1.29-SNAPSHOT"
  :description "Simplfied EDN Query Language for SQL"
  :url "https://github.com/exoscale/seql"
  :license {:name "MIT/ISC License"}
  :pedantic? :abort
  :deploy-repositories [["snapshots" :clojars]
                        ["releases"  :clojars]]
  :profiles {:dev     {:dependencies   [[com.h2database/h2 "1.4.200"]]
                       :plugins        [[lein-cljfmt       "0.6.7"]]
                       :pedantic?      :warn
                       :source-paths   ["dataset"]
                       :resource-paths ["dataset"]}
             :uberjar {:pedantic? :abort}}
  :dependencies [[org.clojure/clojure               "1.10.3"]
                 [com.github.seancorfield/next.jdbc "1.2.731"]
                 [com.github.seancorfield/honeysql  "2.1.818"]
                 [exoscale/coax                     "1.0.0-alpha13"]])

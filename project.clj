(defproject exoscale/seql "0.2.3-SNAPSHOT"
  :description "Simplfied EDN Query Language for SQL"
  :url "https://github.com/exoscale/seql"
  :license {:name "MIT/ISC License"}
  :pedantic? :abort
  :scm {:name "git" :url "https://github.com/exoscale/seql"}
  :deploy-repositories [["snapshots" :clojars]
                        ["releases"  :clojars]]
  :profiles {:dev     {:dependencies   [[com.h2database/h2 "1.4.200"]]
                       :plugins        [[lein-cljfmt       "0.8.0"]]
                       :pedantic?      :warn
                       :source-paths   ["dataset"]
                       :resource-paths ["dataset"]}
             :test {:pedantic? :ignore
                    :plugins [[lein-cloverage             "1.2.2"]]}
             :uberjar {:pedantic? :abort}}
  :aliases {"coverage" ["with-profile" "+test" "cloverage"]}
  :dependencies [[org.clojure/clojure               "1.10.3"]
                 [com.github.seancorfield/next.jdbc "1.2.737"]
                 [com.github.seancorfield/honeysql  "2.1.818"]
                 [exoscale/coax                     "1.0.0-alpha14"]])

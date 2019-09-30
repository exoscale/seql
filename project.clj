(defproject exoscale/seql "0.1.4-SNAPSHOT"
  :description "Simplfied EDN Query Language for SQL"
  :url "https://github.com/exoscale/bundestore"
  :license {:name "ISC"}
  :dependencies [[org.clojure/clojure   "1.10.1"]
                 [org.clojure/java.jdbc "0.7.10"]
                 [honeysql              "0.9.6"]
                 [camel-snake-kebab     "0.4.0"]]
  :plugins [[lein-codox "0.10.7"]]
  :aliases {"kaocha" ["with-profile" "+dev" "run" "-m" "kaocha.runner"]}
  :codox {:source-uri  "https://github.com/exoscale/seql/blob/{version}/{filepath}#L{line}"
          :doc-files ["README.md" "doc/quickstart.md" "doc/sandbox.md" "doc/seql.md"]
          :namespaces [#"^seql\.(?!spec)"]
          :metadata    {:doc/format :markdown}}
  :deploy-repositories [["snapshots" :clojars] ["releases" :clojars]]
  :profiles {:dev {:dependencies   [[mysql/mysql-connector-java "8.0.17"]
                                    [com.h2database/h2          "1.4.199"]
                                    [lambdaisland/kaocha        "0.0-529"]]
                   :source-paths   ["dataset"]
                   :resource-paths ["dataset"]}})

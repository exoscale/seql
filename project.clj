(defproject exoscale/seql "0.1.29-SNAPSHOT"
  :description "Simplfied EDN Query Language for SQL"
  :url "https://github.com/exoscale/seql"
  :license {:name "MIT/ISC License"}
  :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
  :pedantic? :abort
  :codox {:source-uri "https://github.com/exoscale/seql/blob/{version}/{filepath}#L{line}"
          :doc-files  ["README.md" "doc/quickstart.md" "doc/sandbox.md" "doc/seql.md"]
          :namespaces [#"^seql\.(?!spec)"]
          :metadata   {:doc/format :markdown}}
  :aliases {"kaocha" ["with-profile" "+dev" "run" "-m" "kaocha.runner"]
            "junit"  ["with-profile" "+dev" "run" "-m" "kaocha.runner"
                      "--plugin" "kaocha.plugin/junit-xml" "--junit-xml-file"
                      "target/junit/results.xml"]}
  :deploy-repositories [["snapshots" :clojars] ["releases" :clojars]]
  :profiles {:dev     {:dependencies   [[lambdaisland/kaocha           "0.0-554"]
                                        [lambdaisland/kaocha-junit-xml "0.0-70"]
                                        [com.h2database/h2             "1.4.199"]]
                       :plugins        [[lein-cljfmt "0.6.7"]]
                       :pedantic?      :warn
                       :source-paths   ["dataset"]
                       :resource-paths ["dataset"]}
             :uberjar {:pedantic? :abort}}
  :dependencies [[org.clojure/clojure                 "1.10.3"]
                 [com.github.seancorfield/next.jdbc   "1.2.731"]
                 [honeysql/honeysql                   "1.0.461"]
                 [camel-snake-kebab/camel-snake-kebab "0.4.2"]
                 [exoscale/coax                       "1.0.0-alpha13"]
                 [exoscale/cloak                      "0.1.8"]]
  :source-paths ["src"])

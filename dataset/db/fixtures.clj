(ns db.fixtures
  (:require [clojure.java.jdbc  :as jdbc]
            [clojure.java.io    :as io]
            [clojure.edn        :as edn]))

(def jdbc-config
  {:connection-uri "jdbc:h2:mem:privnet;DB_CLOSE_DELAY=-1"})

(defn cleanup
  []
  (jdbc/db-do-commands
   jdbc-config
   ["DROP TABLE IF EXISTS account"
    "DROP TABLE IF EXISTS user"
    "DROP TABLE IF EXISTS invoice"
    "DROP TABLE IF EXISTS invoiceline"
    (jdbc/create-table-ddl
     :account
     [[:id "int not null auto_increment"]
      [:name "varchar(32) not null"]
      [:state "varchar(32) not null"]])
    (jdbc/create-table-ddl
     :user
     [[:id "int not null auto_increment"]
      [:account_id "int not null"]
      [:name "varchar(32) not null"]
      [:email "varchar(32)"]])
    (jdbc/create-table-ddl
     :invoice
     [[:id "int not null auto_increment"]
      [:account_id "int not null"]
      [:state "varchar(32) not null"]
      [:total "int"]])
    (jdbc/create-table-ddl
     :invoiceline
     [[:id "int not null auto_increment"]
      [:invoice_id "int not null"]
      [:product "varchar(32) not null"]
      [:quantity "int"]])]))

(defn load-fixtures
  [dataset]
  (cleanup)
  (doseq [[k v] (-> (format "db/%s.edn" (name dataset))
                    (io/resource)
                    (slurp)
                    (edn/read-string))]
    (jdbc/insert-multi! jdbc-config k v)))

(defn with-db-fixtures
  [dataset]
  (fn [f]
    (load-fixtures dataset)
    (f)
    (cleanup)))

(ns db.fixtures
  (:require [next.jdbc          :as jdbc]
            [next.jdbc.sql      :as sql]
            [clojure.string     :as str]
            [clojure.java.io    :as io]
            [clojure.edn        :as edn]))

(def jdbc-config "jdbc:h2:mem:privnet;DB_CLOSE_DELAY=-1")

(defn create-table-ddl
  [table-id cols]
  (format "CREATE TABLE %s (%s)"
          (name table-id)
          (str/join ", "
                    (map (fn [[id def]] (format "%s %s" (name id) def))
                            cols))))

(defn cleanup
  []
  (run! #(jdbc/execute! jdbc-config [%])
        ["DROP TABLE IF EXISTS account"
         "DROP TABLE IF EXISTS user"
         "DROP TABLE IF EXISTS invoice"
         "DROP TABLE IF EXISTS invoiceline"
         (create-table-ddl
          :account
          [[:id "int not null auto_increment"]
           [:name "varchar(32) not null"]
           [:state "varchar(32) not null"]])
         (create-table-ddl
          :user
          [[:id "int not null auto_increment"]
           [:account_id "int not null"]
           [:name "varchar(32) not null"]
           [:email "varchar(32)"]])
         (create-table-ddl
          :invoice
          [[:id "int not null auto_increment"]
           [:account_id "int not null"]
           [:state "varchar(32) not null"]
           [:total "int"]])
         (create-table-ddl
          :invoiceline
          [[:id "int not null auto_increment"]
           [:invoice_id "int not null"]
           [:product "varchar(32) not null"]
           [:quantity "int"]])]))

(defn load-fixtures
  [dataset]
  (cleanup)
  (doseq [[k [h & r]] (-> (format "db/%s.edn" (name dataset))
                    (io/resource)
                    (slurp)
                    (edn/read-string))]
    (sql/insert-multi! jdbc-config
                       k
                       h
                       r)))

(defn with-db-fixtures
  [dataset]
  (fn [f]
    (load-fixtures dataset)
    (f)
    (cleanup)))

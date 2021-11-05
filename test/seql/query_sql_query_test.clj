(ns seql.query-sql-query-test
  (:require [seql.query   :refer [into-query sql-query]]
            [clojure.test :refer [deftest are testing]]))

(def schema
  {:a {:table     :a
       :fields    [:a/state :a/name]
       :relations {:a/b {:type          :one-to-many
                         :remote-entity :b
                         :remote-id     :b/a-id
                         :local-id      :a/id}}}
   :b {:table  :b
       :fields [:b/name]}})

(deftest into-query-test
  (testing "query preparation with for-query"
    (are [honey params]
         (= honey (into-query schema params))

      {:select [:a.id] :from :a}
      {:table      :a
       :fields     [:a/id]
       :ident?     false
       :selections [:a.id]
       :conditions nil
       :joins      []}

      {:select [:a.id] :from :a :where [:= :a.id 0]}
      {:table      :a
       :fields     [:a/id]
       :ident?     true
       :selections [:a.id]
       :conditions [[:= :a.id 0]]
       :joins      []}

      {:select [:a.id :b.id]
       :from :a
       :where [:and [:= :a.id 0] [:= :b.name "foo"]]
       :left-join [:b [:= :a.id :b.a_id]]}
      {:table      :a
       :fields     [:a/id {:a/b [:b/id]}]
       :ident?     true
       :selections [:a.id :b.id]
       :conditions [[:= :a.id 0] [:= :b.name "foo"]]
       :joins      [:a/b]})))

(deftest sql-query-test
  (testing "query formatting with sql-query"
    (are [sql params]
         (= sql (sql-query schema params))

      ["SELECT a.id FROM a"]
      {:table      :a
       :fields     [:a/id]
       :ident?     false
       :selections [:a.id]
       :conditions nil
       :joins      []}

      ["SELECT a.id FROM a WHERE a.id = ?" 0]
      {:table      :a
       :fields     [:a/id]
       :ident?     true
       :selections [:a.id]
       :conditions [[:= :a.id 0]]
       :joins      []}

      ["SELECT a.id, b.id FROM a LEFT JOIN b ON a.id = b.a_id WHERE (a.id = ?) AND (b.name = ?)"
       0
       "foo"]
      {:table      :a
       :fields     [:a/id {:a/b [:b/id]}]
       :ident?     true
       :selections [:a.id :b.id]
       :conditions [[:= :a.id 0] [:= :b.name "foo"]]
       :joins      [:a/b]})))

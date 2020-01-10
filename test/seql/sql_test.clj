(ns seql.sql-test
  "Tests that validate the shape of generated SQL queries"
  (:require [seql.core     :refer :all]
            [clojure.test  :refer :all]
            [honeysql.core :as sql]))

(deftest query-test

  (let [env {:schema
             {:a {:table     :a
                  :ident     :a/id
                  :fields    [:a/id]
                  :relations {:a/b {:type          :one-to-many
                                    :local-id      :a/id
                                    :remote-id     :b/a-id
                                    :remote-entity :b}}}
              :b {:table     :b
                  :ident     :b/id
                  :fields    [:b/id]
                  :relations {:b/c {:type          :one-to-many
                                    :local-id      :b/id
                                    :remote-id     :c/b-id
                                    :remote-entity :c}}}
              :c {:table  :c
                  :ident  :c/id
                  :fields [:c/id]}}}]

    (testing "basic single-layer query"
      (is (= ["SELECT a.id AS a__id FROM a a    "]
             (sql/format
              (sql-query env :a [:a/id] [])))))

    (testing "query for one nested entity"
      (is (= [(str "SELECT a.id AS a__id, b.id AS b__id "
                   "FROM a a LEFT JOIN b b ON a.id = b.a_id   ")]
             (sql/format
              (sql-query env :a [:a/id {:a/b [:b/id]}] [])))))

    (testing "query for a two-level nested entity"
      (is (= [(str "SELECT a.id AS a__id, b.id AS b__id, c.id AS c__id "
                   "FROM a a LEFT JOIN b b ON a.id = b.a_id "
                   "LEFT JOIN c c ON b.id = c.b_id   ")]
             (sql/format
              (sql-query env :a [:a/id {:a/b [:b/id {:b/c [:c/id]}]}] [])))))))
(def env *1)

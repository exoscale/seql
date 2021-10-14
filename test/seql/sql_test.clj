(ns seql.sql-test
  "Tests that validate the shape of generated SQL queries"
  (:require [seql.core     :refer [sql-query]]
            [clojure.test  :refer [deftest is testing]]
            [honey.sql     :as sql]))

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
      (is (= ["SELECT a.id AS a__id FROM a AS a"]
             (sql/format
              (first
               (sql-query env :a [:a/id] []))))))

    (testing "query for one nested entity"
      (is (= [(str "SELECT a.id AS a__id, b.id AS b__id "
                   "FROM a AS a LEFT JOIN b AS b ON a.id = b.a_id")]
             (sql/format
              (first
               (sql-query env :a [:a/id {:a/b [:b/id]}] []))))))

    (testing "query for a two-level nested entity"
      (is (= [(str "SELECT a.id AS a__id, b.id AS b__id, c.id AS c__id "
                   "FROM a AS a LEFT JOIN b AS b ON a.id = b.a_id "
                   "LEFT JOIN c AS c ON b.id = c.b_id")]
             (sql/format
              (first
               (sql-query env :a [:a/id {:a/b [:b/id {:b/c [:c/id]}]}] []))))))))

(deftest many-to-many-test

  (let [env {:schema
             {:a {:table     :a
                  :ident     :a/id
                  :fields    [:a/id]
                  :relations {:a/b {:type               :many-to-many
                                    :local-id           :a/id
                                    :intermediate       :i
                                    :intermediate-left  :i/a-id
                                    :intermediate-right :i/b-id
                                    :remote-id          :b/id
                                    :remote-entity      :b}}}
              :b {:table  :b
                  :ident  :b/id
                  :fields [:b/id]}
              :i {:table  :i
                  :ident  :i/id
                  :fields [:i/id :i/a-id :i/b-id]}}}]

    (testing "basic single-layer query"
      (is (= ["SELECT a.id AS a__id FROM a AS a"]
             (sql/format
              (first
               (sql-query env :a [:a/id] []))))))

    (testing "many-to-many query"
      (is (= ["SELECT a.id AS a__id, b.id AS b__id FROM a AS a LEFT JOIN i ON a.id = i.a_id LEFT JOIN b AS b ON i.b_id = b.id"]
             (sql/format
              (first
               (sql-query env :a [:a/id {:a/b [:b/id]}]
                          []))))))))

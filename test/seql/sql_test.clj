(ns seql.sql-test
  "Tests that validate the shape of generated SQL queries"
  (:require [seql.query    :as q]
            [seql.params   :as p]
            [clojure.test  :refer [deftest is testing]]))

(defn sql-format
  [schema entity fields conditions]
  (first
   (q/sql-query schema (p/for-query schema entity fields conditions))))

(deftest query-test

  (let [schema {:a {:table     :a
                    :fields    [:a/id]
                    :relations {:a/b {:type          :one-to-many
                                      :local-id      :a/id
                                      :remote-id     :b/a-id
                                      :remote-entity :b}}}
                :b {:table     :b
                    :fields    [:b/id]
                    :relations {:b/c {:type          :one-to-many
                                      :local-id      :b/id
                                      :remote-id     :c/b-id
                                      :remote-entity :c}}}
                :c {:table  :c
                    :fields [:c/id]}}]

    (testing "basic single-layer query"
      (is (= "SELECT a.id FROM a"
             (sql-format schema :a [:a/id] []))))

    (testing "query for one nested entity"
      (is (= (str "SELECT a.id, b.id "
                  "FROM a LEFT JOIN b ON a.id = b.a_id")
             (sql-format schema :a [:a/id {:a/b [:b/id]}] []))))

    (testing "query for a two-level nested entity"
      (is (= (str "SELECT a.id, b.id, c.id "
                  "FROM a LEFT JOIN b ON a.id = b.a_id "
                  "LEFT JOIN c ON b.id = c.b_id")
             (sql-format schema :a [:a/id {:a/b [:b/id {:b/c [:c/id]}]}] []))))))

(deftest many-to-many-test

  (let [schema {:a {:table     :a
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
                    :fields [:i/id :i/a-id :i/b-id]}}]

    (testing "basic single-layer query"
      (is (= "SELECT a.id FROM a" (sql-format schema :a [:a/id] []))))

    (testing "many-to-many query"
      (is (= "SELECT a.id, b.id FROM a LEFT JOIN i ON a.id = i.a_id LEFT JOIN b ON i.b_id = b.id"
             (sql-format schema :a [:a/id {:a/b [:b/id]}] []))))))

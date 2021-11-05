(ns seql.params-condition-test
  "
  Conditions are optional selectors for query that restrict the output.
  They map to SQL where conditions. Conditions may be either static, in
  which case a single field value is looked up, field conditions where a
  value for the field is provided at query time, or functions which yield
  the appropriate where vector for honeysql.
  "
  (:require [seql.params        :refer [for-query expand-condition]]
            [clojure.test       :refer [deftest is testing]]
            [seql.query         :as q]
            [clojure.spec.alpha :as s]))

(defn sql-format
  [schema entity fields conditions]
  (q/sql-query schema (for-query schema entity fields conditions)))

(s/def :a/state #{:active :suspended})

(deftest condition-test
  (let [schema {:a {:table      :a
                    :fields     [:a/id :a/state :a/name]
                    :conditions {:a/active {:type  :static
                                            :field :a/state
                                            :value :active}
                                 :a/inline {:type    :fn
                                            :arity   2
                                            :handler #(vector := %1 %2)}}}}]

    (testing "adding static condition"
      (is (= [:= :a.state "active"]
             (expand-condition schema [:a/active]))))

    (testing "adding field condition"
      (is (= [:= :a.state "active"]
             (expand-condition schema [:a/state :active])))

      (is (= [:= :a.state "suspended"]
             (expand-condition schema [:a/state :suspended]))))

    (testing "field conditions must respect arity"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"bad arity for field condition: :a/state"
                            (expand-condition schema [:a/state]))))

    (testing "adding multiple fields creates a :in clause"
      (is (= [:in :a.state ["active" "suspended"]]
             (expand-condition schema [:a/state "active" "suspended"]))))

    (testing "function conditions must respect arity"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"bad arity for condition: :a/inline"
                            (expand-condition schema [:a/inline]))))

    (testing "function conditions are executed"
      (is (= [:= :a.field "value"]
             (expand-condition schema [:a/inline :a.field "value"]))))))

(deftest condition-table-alias-test
  (let [schema {:a {:table      :b
                    :fields     [:a/id :a/state :a/name]
                    :conditions {:a/active {:type  :static
                                            :field :a/state
                                            :value :active}
                                 :a/inline {:type    :fn
                                            :arity   2
                                            :handler #(vector := %1 %2)}}}}]

    (testing "adding static condition"
      (is (= [:= :b.state "active"]
             (expand-condition schema [:a/active]))))

    (testing "adding field condition"
      (is (= [:= :b.state "active"]
             (expand-condition schema [:a/state :active])))

      (is (= [:= :b.state "suspended"]
             (expand-condition schema [:a/state :suspended]))))

    (testing "field conditions must respect arity"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"bad arity for field condition: :a/state"
                            (expand-condition schema [:a/state]))))

    (testing "adding multiple fields creates a :in clause"
      (is (= [:in :b.state ["active" "suspended"]]
             (expand-condition schema [:a/state "active" "suspended"]))))

    (testing "function conditions must respect arity"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"bad arity for condition: :a/inline"
                            (expand-condition schema [:a/inline]))))

    (testing "function conditions are executed"
      (is (= [:= :b.field "value"]
             (expand-condition schema [:a/inline :b.field "value"]))))))

(deftest condition-alias-sql-test
  (let [schema {:a {:table      :b
                    :ident      :a/id
                    :fields     [:a/id :a/state]
                    :conditions {:a/active {:type  :static
                                            :field :a/state
                                            :value :active}}}}]
    (is (= ["SELECT b.id FROM b WHERE b.state = ?" "active"]
           (sql-format schema :a [:a/id] [[:a/active]])))))

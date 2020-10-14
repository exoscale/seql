(ns seql.condition-test
  "
  Conditions are optional selectors for query that restrict the output.
  They map to SQL where conditions. Conditions may be either static, in
  which case a single field value is looked up, field conditions where a
  value for the field is provided at query time, or functions which yield
  the appropriate where vector for honeysql.
  "
  (:require [seql.core     :refer :all]
            [clojure.test  :refer :all]
            [honeysql.core :as sql]
            [clojure.spec.alpha :as s]))

(s/def :a/state #{:active :suspended})

(deftest condition-test
  (let [empty-query {}
        schema      {:a {:table      :a
                         :ident      :a/id
                         :fields     [:a/id :a/state :a/name]
                         :conditions {:a/active {:type  :static
                                                 :field :a/state
                                                 :value :active}
                                      :a/state  {:type  :field
                                                 :field :a/state}
                                      :a/inline {:type    :fn
                                                 :arity   2
                                                 :handler #(vector := %1 %2)}}
                         :transforms {:a/state [keyword name]}}}]

    (testing "adding static condition"
      (is (= {:where [:= :a.state "active"]}
             (add-condition schema empty-query [:a/active]))))

    (testing "adding field condition"
      (is (= {:where [:= :a.state "active"]}
             (add-condition schema empty-query [:a/state :active])))

      (is (= {:where [:= :a.state "suspended"]}
             (add-condition schema empty-query [:a/state :suspended]))))

    (testing "field conditions must respect arity"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"bad arity for field condition: :a/state"
                            (add-condition schema empty-query [:a/state]))))

    (testing "adding multiple fields creates a :in clause"
      (is (= {:where [:in :a.state ["active" "suspended"]]}
             (add-condition schema empty-query
                            [:a/state "active" "suspended"]))))

    (testing "function conditions must respect arity"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"bad arity for condition: :a/inline"
                            (add-condition schema empty-query [:a/inline]))))

    (testing "function conditions are executed"
      (is (= {:where [:= :a.field "value"]}
             (add-condition schema empty-query
                            [:a/inline :a.field "value"]))))))

(deftest condition-table-alias-test
  (let [empty-query {}
        schema      {:a {:table      :b
                         :ident      :a/id
                         :fields     [:a/id :a/state :a/name]
                         :conditions {:a/active {:type  :static
                                                 :field :a/state
                                                 :value :active}
                                      :a/state  {:type  :field
                                                 :field :a/state}
                                      :a/inline {:type    :fn
                                                 :arity   2
                                                 :handler #(vector := %1 %2)}}
                         :transforms {:a/state [keyword name]}}}]

    (testing "adding static condition"
      (is (= {:where [:= :a.state "active"]}
             (add-condition schema empty-query [:a/active]))))

    (testing "adding field condition"
      (is (= {:where [:= :a.state "active"]}
             (add-condition schema empty-query [:a/state :active])))

      (is (= {:where [:= :a.state "suspended"]}
             (add-condition schema empty-query [:a/state :suspended]))))

    (testing "field conditions must respect arity"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"bad arity for field condition: :a/state"
                            (add-condition schema empty-query [:a/state]))))

    (testing "adding multiple fields creates a :in clause"
      (is (= {:where [:in :a.state ["active" "suspended"]]}
             (add-condition schema empty-query
                            [:a/state "active" "suspended"]))))

    (testing "function conditions must respect arity"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"bad arity for condition: :a/inline"
                            (add-condition schema empty-query [:a/inline]))))

    (testing "function conditions are executed"
      (is (= {:where [:= :a.field "value"]}
             (add-condition schema empty-query
                            [:a/inline :a.field "value"]))))))

(deftest condition-alias-sql-test
  (let [schema {:a {:table      :b
                    :ident      :a/id
                    :fields     [:a/id :a/state]
                    :conditions {:a/active {:type  :static
                                            :field :a/state
                                            :value :active}}
                    :transforms {:a/state [keyword name]}}}]
    (is (= ["SELECT a.id AS a__id FROM b a  WHERE a.state = ?" "active"]
           (-> (sql-query {:schema schema} :a [:a/id] [[:a/active]])
               (first)
               (sql/format))))))

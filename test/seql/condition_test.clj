(ns seql.condition-test
  "
  Conditions are optional selectors for query that restrict the output.
  They map to SQL where conditions. Conditions may be either static, in
  which case a single field value is looked up, field conditions where a
  value for the field is provided at query time, or functions which yield
  the appropriate where vector for honeysql.
  "
  (:require [seql.core     :refer :all]
            [clojure.test  :refer :all]))

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
                            (add-condition schema empty-query [:a/state])))

      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"bad arity for field condition: :a/state"
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

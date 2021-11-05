(ns seql.params-query-test
  (:require [seql.params  :refer [for-query]]
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

(deftest for-query-test
  (testing "query preparation with for-query"
    (are [result entity fields conditions]
         (= result (for-query schema entity fields conditions))
      {:table      :a
       :fields     [:a/id]
       :ident?     false
       :selections [:a.id]
       :conditions nil
       :joins      []}
      :a/a [:a/id] []

      {:table      :a
       :fields     [:a/id]
       :ident?     true
       :selections [:a.id]
       :conditions [[:= :a.id 0]]
       :joins      []}
      [:a/id 0] [:a/id] []

      {:table      :a
       :fields     [:a/id {:a/b [:b/id]}]
       :ident?     true
       :selections [:a.id :b.id]
       :conditions [[:= :a.id 0] [:= :b.name "foo"]]
       :joins      [:a/b]}
      [:a/id 0] [:a/id {:a/b [:b/id]}] [[:b/name "foo"]])))

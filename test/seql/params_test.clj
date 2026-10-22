(ns seql.params-test
  (:require [seql.params :as p]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest testing are]]))

(def abc-schema
  {:a {:table :a}
   :b {:table :b}
   :c {:table :x}})

(defn find-selections
  [schema y]
  (#'p/find-selections schema (s/conform :seql.query/seql-query y)))

(defn find-joins
  [y]
  (#'p/find-joins (s/conform :seql.query/seql-query y)))

(deftest find-selections-test
  (testing "field extraction"
    (are [x y] (= x (find-selections abc-schema y))
      [[:a.id (keyword "a,id")]]                                                   [:a/id]
      [[:a.id (keyword "a,id")] [:a.name (keyword "a,name")]]                      [:a/id :a/name]
      [[:a.id (keyword "a,id")] [:b.id (keyword "b,id")]]                          [:a/id {:a/b [:b/id]}]
      [[:a.id (keyword "a,id")] [:b.id (keyword "b,id")] [:x.id (keyword "x,id")]] [:a/id {:a/b [:b/id {:b/c [:c/id]}]}])))

(deftest find-joins-test
  (testing "join extraction"
    (are [x y] (= x (find-joins y))
      []          [:a/id]
      []          [:a/id :a/name]
      [:a/b]      [:a/id {:a/b [:b/id]}]
      [:a/b :b/c] [:a/id {:a/b [:b/id {:b/c [:c/id]}]}])))

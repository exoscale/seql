(ns seql.schema-test
  (:require [clojure.test :refer [deftest testing are is]]
            [seql.schema  :as schema]))

(def abc-schema
  {:a {:table :a :fields [:a/id :a/name] :mutations {:a/create ::a-create}}
   :b {:table :b :fields [:b/id :b/ip6address] :overrides {:b/ip6address :ip6}}
   :c {:table :x :fields [:c/id :c/foo] :overrides {:c/foo :bar}}})

(deftest resolve-entity-test
  (testing "resolve entity"
    (are [x y] (= (schema/resolve-entity x) y)
      :short-qualifier            :short-qualifier
      :fully.qualified.ns         :fully.qualified.ns
      :short-qualifier/foo        :short-qualifier
      :fully.qualified.ns/foo     :fully.qualified.ns
      [:short-qualifier/foo 0]    :short-qualifier
      [:fully.qualified.ns/foo 0] :fully.qualified.ns))

  (testing "bad entity specifications"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"bad entity specification"
                          (schema/resolve-entity "hello")))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"bad entity specification"
                          (schema/resolve-entity nil)))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"bad entity specification"
                          (schema/resolve-entity 'foo)))))

(deftest resolve-mutation-test
  (testing "resolve mutation success"
    (is (= ::a-create (schema/resolve-mutation abc-schema :a/create))))
  (testing "resolve mutation failure"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"unknown mutation: :a/create2"
                          (schema/resolve-mutation abc-schema :a/create2)))))

(deftest resolve-table-test
  (testing "resolve table success"
    (are [x y] (= (schema/resolve-table abc-schema x) y)
      :a/id     :a
      [:a/id 0] :a
      :a        :a
      :c        :x
      :c/id     :x
      [:c/id 0] :x))
  (testing "resolve table failure"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"unkwown table for: :x/i"
                          (schema/resolve-table abc-schema :x/id)))))

(deftest resolve-override-test
  (testing "resolve override success and failure"
    (are [x y] (= (schema/resolve-override abc-schema x) y)
      :a/id         nil
      :b/id         nil
      :b/ip6address :ip6)))

(deftest resolve-field-test
  (testing "resolve override success and failure"
    (are [x y] (= (schema/resolve-field abc-schema x) y)
      :a/id         :a.id
      :b/ip6address :b.ip6
      :c/foo        :x.bar)))

(deftest as-row-test
  (testing "successful translation to an SQL row"
    (are [x y] (= x (schema/as-row abc-schema :c y))
      {:id 1 :bar 2} {:c/id 1 :c/foo 2})))

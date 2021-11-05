(ns seql.tree-test
  (:require [seql.tree    :as tree]
            [seql.helpers :refer [make-schema entity field has-many]]
            [clojure.test :refer [deftest testing is]]))

(def account-schema
  (make-schema
   (entity :account
           (field :id)
           (has-many :users [:id :user/id]))
   (entity :user
           (field :id)
           (has-many :users [:id :user/account-id]))))

(def abc-schema
  (make-schema
   (entity :a (field :id) (has-many :b [:id :b/id]))
   (entity :b (field :id) (has-many :c [:id :c/id]))
   (entity :c (field :id))))

(deftest build-tree-test

  (testing "no recomposition has no effect"

    (let [schema  {}
          fields  {:fields [:a/id]}
          records [{:a/id 0}
                   {:a/id 1}
                   {:a/id 2}
                   {:a/id 3}
                   {:a/id 4}
                   {:a/id 5}
                   {:a/id 6}
                   {:a/id 7}
                   {:a/id 8}
                   {:a/id 9}]]
      (is (= records (tree/build schema fields (shuffle records))))))

  (testing "simple one layer recomposition"
    (let [records [{:account/id 0 :user/id 0}
                   {:account/id 0 :user/id 1}
                   {:account/id 0 :user/id 2}
                   {:account/id 0 :user/id 3}
                   {:account/id 0 :user/id 4}
                   {:account/id 1 :user/id 5}
                   {:account/id 1 :user/id 6}
                   {:account/id 1 :user/id 7}
                   {:account/id 1 :user/id 8}
                   {:account/id 1 :user/id 9}]
          fields  {:fields [:account/id {:account/users [:user/id]}]}
          result  [{:account/id 0 :account/users [{:user/id 0}
                                                  {:user/id 1}
                                                  {:user/id 2}
                                                  {:user/id 3}
                                                  {:user/id 4}]}
                   {:account/id 1 :account/users [{:user/id 5}
                                                  {:user/id 6}
                                                  {:user/id 7}
                                                  {:user/id 8}
                                                  {:user/id 9}]}]]

      (is (= result (tree/build account-schema fields (shuffle records))))))

  (testing "two layer recomposition"
    (let [records [{:a/id 0 :b/id 0 :c/id 0}
                   {:a/id 0 :b/id 0 :c/id 1}
                   {:a/id 0 :b/id 0 :c/id 2}
                   {:a/id 0 :b/id 1 :c/id 3}
                   {:a/id 0 :b/id 1 :c/id 4}
                   {:a/id 0 :b/id 1 :c/id 5}
                   {:a/id 0 :b/id 2 :c/id 6}
                   {:a/id 0 :b/id 2 :c/id 7}
                   {:a/id 0 :b/id 2 :c/id 8}

                   {:a/id 1 :b/id 3 :c/id 9}
                   {:a/id 1 :b/id 3 :c/id 10}
                   {:a/id 1 :b/id 3 :c/id 11}
                   {:a/id 1 :b/id 4 :c/id 12}
                   {:a/id 1 :b/id 4 :c/id 13}
                   {:a/id 1 :b/id 4 :c/id 14}
                   {:a/id 1 :b/id 5 :c/id 15}
                   {:a/id 1 :b/id 5 :c/id 16}
                   {:a/id 1 :b/id 5 :c/id 17}]
          fields {:fields [:a/id {:a/b [:b/id {:b/c [:c/id]}]}]}
          result [{:a/id 0
                   :a/b  [{:b/id 0 :b/c [{:c/id 0} {:c/id 1} {:c/id 2}]}
                          {:b/id 1 :b/c [{:c/id 3} {:c/id 4} {:c/id 5}]}
                          {:b/id 2 :b/c [{:c/id 6} {:c/id 7} {:c/id 8}]}]}
                  {:a/id 1
                   :a/b  [{:b/id 3 :b/c [{:c/id 9} {:c/id 10} {:c/id 11}]}
                          {:b/id 4 :b/c [{:c/id 12} {:c/id 13} {:c/id 14}]}
                          {:b/id 5 :b/c [{:c/id 15} {:c/id 16} {:c/id 17}]}]}]]

      (is (= result (tree/build abc-schema fields (shuffle records))))))

  (testing "Recompose relations with map"
    (let [fields  {:fields [:a/id :a/data {:a/b [:b/id :b/data]}]}
          records [{:a/id 0
                    :a/data {:foo 3}
                    :b/id 0
                    :b/data {:foo 0}}
                   {:a/id 0
                    :a/data {:foo 3}
                    :b/id 1
                    :b/data {:foo 1}}
                   {:a/id 0
                    :a/data {:foo 3}
                    :b/id 2
                    :b/data {:foo 2}}
                   {:a/id 0
                    :a/data {:foo 3}
                    :b/id 3
                    :b/data {:foo 3}}
                   {:a/id 0
                    :a/data {:foo 3}
                    :b/id 4
                    :b/data {:foo 4}}
                   {:a/id 0
                    :a/data {:foo 3}
                    :b/id 5}]
          result [{:a/id 0
                   :a/data {:foo 3}
                   :a/b [{:b/id 0 :b/data {:foo 0}}
                         {:b/id 1 :b/data {:foo 1}}
                         {:b/id 2 :b/data {:foo 2}}
                         {:b/id 3 :b/data {:foo 3}}
                         {:b/id 4 :b/data {:foo 4}}
                         {:b/id 5}]}]]
      (is (= result (tree/build abc-schema fields (shuffle records)))))))

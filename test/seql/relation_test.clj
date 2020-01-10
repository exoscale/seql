(ns seql.relation-test
  (:require [seql.core    :refer [recompose-relations]]
            [clojure.test :refer :all]))

(deftest recompose-relations-test

  (testing "no recomposition has no effect"

    (let [fields  [:a/id]
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
      (is (= records (recompose-relations {}  fields (shuffle records))))))

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
          fields  [:account/id {:account/users [:user/id]}]
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

      (is (= result (recompose-relations {}  fields records)))
      (is (= result (recompose-relations {}  fields (shuffle records))))))

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
          fields [:a/id {:a/b [:b/id {:b/c [:c/id]}]}]
          result [{:a/id 0
                   :a/b  [{:b/id 0 :b/c [{:c/id 0} {:c/id 1} {:c/id 2}]}
                          {:b/id 1 :b/c [{:c/id 3} {:c/id 4} {:c/id 5}]}
                          {:b/id 2 :b/c [{:c/id 6} {:c/id 7} {:c/id 8}]}]}
                  {:a/id 1
                   :a/b  [{:b/id 3 :b/c [{:c/id 9} {:c/id 10} {:c/id 11}]}
                          {:b/id 4 :b/c [{:c/id 12} {:c/id 13} {:c/id 14}]}
                          {:b/id 5 :b/c [{:c/id 15} {:c/id 16} {:c/id 17}]}]}]]

      (is (= result (recompose-relations {}  fields records)))
      (is (= result (recompose-relations {}  fields (shuffle records))))))

  (testing "parallel recomposition"
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
          fields [:a/id {:a/b [:b/id {:b/c [:c/id]}]}]
          result [{:a/id 0
                   :a/b  [{:b/id 0 :b/c [{:c/id 0} {:c/id 1} {:c/id 2}]}
                          {:b/id 1 :b/c [{:c/id 3} {:c/id 4} {:c/id 5}]}
                          {:b/id 2 :b/c [{:c/id 6} {:c/id 7} {:c/id 8}]}]}
                  {:a/id 1
                   :a/b  [{:b/id 3 :b/c [{:c/id 9} {:c/id 10} {:c/id 11}]}
                          {:b/id 4 :b/c [{:c/id 12} {:c/id 13} {:c/id 14}]}
                          {:b/id 5 :b/c [{:c/id 15} {:c/id 16} {:c/id 17}]}]}]]

      (is (= result (recompose-relations {}  fields records)))
      (is (= result (recompose-relations {}  fields (shuffle records))))))

  (testing "weeding out empty nested entities"

    ;; When doing left joins we have cases where no sub entity exists
    ;; in this case, ensure that they get weeded out of the output list

    (let [records [{:a 1 :b 4 :c nil}
                   {:a 1 :b 5 :c nil}
                   {:a 1 :b 6 :c nil}
                   {:a 2 :b nil :c nil}]
          fields [:a {:b [:b]} {:c [:c]}]
          result [{:a 1 :b [{:b 4} {:b 5} {:b 6}] :c []}
                  {:a 2 :b []                     :c []}]]

      (is (= result (recompose-relations {} fields (shuffle records)))))

    (let [records [{:a 4 :b nil :c 1}
                   {:a 3 :b 7 :c 2}
                   {:a 3 :b 8 :c 2}]
          fields [:a {:b [:b]} {:c [:c]}]
          result [{:a 3 :b [{:b 7} {:b 8}] :c [{:c 2}]}
                  {:a 4 :b []              :c [{:c 1}]}]]
      (is (= result (recompose-relations {}  fields (shuffle records)))))))

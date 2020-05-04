(ns seql.coerce-test
  (:require [clojure.test :as t :refer [deftest is]]
            [clojure.spec.alpha :as s]
            [seql.coerce :as c]))

(s/def ::k keyword?)
(s/def ::kxs #{:foo :bar :baz})

(s/def ::i integer?)
(s/def ::ixs #{1 2 3})

(s/def ::m map?)

(deftest test-writer
  (is (= (c/write ::unknown-spec 1) 1))
  (is (= (c/write nil 1) 1))
  (is (= (c/write ::k :foo) "foo"))
  (is (= (c/write ::k :foo) "foo"))
  (is (= (c/write ::m {:a 1}) {:a 1}))

  (-> (s/def ::mw map?)
      (c/with-writer! c/edn-writer))

  (is (= (c/write ::mw {:a 1}) "{:a 1}")))

(deftest test-reader
  (is (= (c/read ::unknown-spec 1) 1))
  (is (= (c/read nil 1) 1))
  (is (= (c/read ::k "foo") :foo))
  (is (= (c/read ::m {:a 1}) {:a 1}))
  (-> (s/def ::mw map?)
      (c/with-reader! c/edn-reader))

  (is (= (c/read ::mw "{:a 1}") {:a 1})))

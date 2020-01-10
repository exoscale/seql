(ns seql.core-test
  (:require [seql.core    :as c]
            [seql.helpers :refer [make-schema ident field compound
                                  mutation transform has-many has-one condition
                                  entity]]
            [clojure.test :refer [use-fixtures testing deftest is]]
            [db.fixtures  :refer [with-db-fixtures]]))

(use-fixtures :each (with-db-fixtures :small))

(def schema
  "Setup a realistic schema"
  (make-schema
   (entity :account
           (field :id          (ident))
           (field :name        (ident))
           (field :state       (transform :keyword))
           (has-many :users    [:id :user/account-id])
           (has-many :invoices [:id :invoice/account-id])

           (condition :active  :state :active)
           (condition :state))
   (entity :user
           (field :id          (ident))
           (field :name        (ident))
           (field :email))
   (entity :invoice
           (field :id          (ident))
           (field :state       (transform :keyword))
           (field :total)
           (compound :paid?    [state] (= state :paid))
           (has-many :lines    [:id :line/invoice-id])

           (condition :unpaid  :state :unpaid)
           (condition :paid    :state :paid))
   (entity :product
           (field :id (ident))
           (field :name (ident)))
   (entity [:line :invoiceline]
           (field :id          (ident))
           (has-one :product [:product-id :product/id])
           (field :quantity))))

(def env
  "Setup a minimal env for our schema"
  {:schema schema})

(deftest entity-schema-test
  (testing "missing entity in schema"
    (is (= [:foo nil]
           (c/entity-schema (:schema env)
                            :foo))))

  (testing "matching entity in schema"
    (is (= :account
           (first (c/entity-schema env
                                   :account)))))
  (testing "matching entity with handler+args"
    (is (= :invoice
           (first (c/entity-schema env
                                   [:invoice/unpaid 1 2]))))))

(deftest transforms-test
  (testing "turning qualified-keyword to table id"
    (is (= [:foo.bar :foo__bar] (c/transform-out :foo/bar))))

  (testing "turning qualified-keyword to table id for join context"
    (is (= :foo.bar (c/transform-for-join :foo/bar)))))

(deftest add-ident-test
  (testing "generation of ident selection clause"
    (is (= {:where [:= :user.id "some-uuid"]}
           (c/add-ident {}
                        :user
                        :user/id
                        "some-uuid"))))
  (testing "generation of ident selection clause with table name"
    (is (= {:where [:= :line.id "some-uuid"]}
           (c/add-ident {}
                        :line
                        :line/id
                        "some-uuid")))))

(deftest process-join-test
  (testing "left-join: supplied remote name"
    (is (= {:left-join [[:= :account.id :rem-account.name] [:accounts :account]]}
           (c/process-join {}
                           {:entity :account
                            :table :accounts}
                           {:type :one-to-many
                            :local-id :account/id
                            :remote-id :rem-account/id
                            :remote-name :rem-account/name}))))

  (testing "left-join: missing remote-name"
    (is (= {:left-join [[:= :account.id :rem-account.id] [:accounts :account]]}
           (c/process-join {}
                           {:entity :account
                            :table :accounts}
                           {:type :one-to-many
                            :local-id :account/id
                            :remote-id :rem-account/id})))))

(deftest process-field-test
  (testing "unknown field type"
    (is (thrown? clojure.lang.ExceptionInfo
                 ((c/process-field schema {:relations {}
                                           :compounds #{}
                                           :fields [:account/id]
                                           :entity :account})
                  {} :account/foo)))
    (is (thrown? clojure.lang.ExceptionInfo
                 ((c/process-field schema {:relations {}
                                           :compounds #{}
                                           :fields [:account/foo]
                                           :entity :account})
                  {} :account/id))))

  (testing "simple field set"
    (is (= {:select [[:account.id :account__id]]
            :seql.core/meta #:seql.core{:fields [:account/id]}}
           ((c/process-field schema {:relations {} :compounds #{} :fields [:account/id]
                                     :entity :account})
            {}
            :account/id))))

  (testing "simple field rel with compound"
    (is (= {:select [[:invoice.state :invoice__state]]
            :seql.core/meta #:seql.core{:fields [:invoice/paid?]}}
           ((c/process-field schema {:relations {}
                                     :compounds {:invoice/paid? {:source  [:invoice/state]}}
                                     :fields []
                                     :entity :invoice})
            {}
            :invoice/paid?)))))

(deftest build-query-test
  (is (= {:from [[:account :account]]
          :select [[:account.id :account__id]
                   [:account.state :account__state]]
          :left-join []
          :seql.core/meta #:seql.core{:entities #{:account}
                                      :entity :account
                                      :fields [:account/id :account/state]}}
         (c/build-query env
                        :account
                        [:account/id :account/state]))))

(deftest prepare-field-test
  (testing "field has tranform defined"
    (is (= (c/prepare-field schema :invoice/state :foo)
           "foo")))
  (testing "field has no transform -> identity"
    (is (= "bar" (c/prepare-field schema :invoice/id "bar")))
    (is (= :bar (c/prepare-field schema :invoice/id :bar)))))

(deftest test-qualify-key
  (testing "basic id -> keyword convertion"
    (is (= :foo/bar
           (c/qualify-key :foo__bar))))

  (testing "make sure handle properly kebab-case"
    (is (= :foo-one/bar-one
           (c/qualify-key :FooOne__BarOne)))))

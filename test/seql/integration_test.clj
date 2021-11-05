(ns seql.integration-test
  (:require [seql.mutation :refer [mutate!]]
            [clojure.spec.alpha :as s]
            [seql.listener :refer [add-listener remove-listener]]
            [seql.env :as env]
            [seql.query    :as q]
            [seql.helpers       :refer [make-schema entity-from-spec add-create-mutation
                                        has-many-through entity add-update-by-id-mutation
                                        has-many has-one condition add-precondition]]
            [db.fixtures        :refer [jdbc-config with-db-fixtures]]
            [clojure.test       :refer [use-fixtures testing deftest is]]
            [honey.sql.helpers  :as h]
            [seql.readme-test] ;; for the specs
            ))

(use-fixtures :each (with-db-fixtures :small))

(create-ns 'my.entities)
(create-ns 'my.entities.account)
(create-ns 'my.entities.user)
(create-ns 'my.entities.invoice)
(create-ns 'my.entities.invoice-line)
(create-ns 'my.entities.product)
(create-ns 'my.entities.role)
(create-ns 'my.entities.role-users)

(alias 'account 'my.entities.account)
(alias 'user 'my.entities.user)
(alias 'invoice 'my.entities.invoice)
(alias 'invoice-line 'my.entities.invoice-line)
(alias 'product 'my.entities.product)
(alias 'role 'my.entities.role)
(alias 'role-users 'my.entities.role-users)

(def schema
  "As gradually explained in the project's README"
  (make-schema
   (entity-from-spec ::account/account
                     (has-many :users    [:id ::user/account-id])
                     (has-many :invoices [:id ::invoice/account-id])
                     (condition :active  :state :active)
                     (add-create-mutation)
                     (add-update-by-id-mutation ::account/id))
   (entity-from-spec ::user/user)
   (entity-from-spec ::invoice/invoice
                     (has-many :lines    [:id ::invoice-line/invoice-id])
                     (condition :unpaid  :state :unpaid)
                     (condition :paid    :state :paid))
   (entity-from-spec ::product/product)
   (entity-from-spec [::invoice-line/invoice-line :invoiceline]
                     (has-one :product [:product-id ::product/id]))
   (entity-from-spec [::role/role :user_role]
                     (has-many-through :users [:id ::role-users/role-id ::role-users/user-id  ::user/id]))
   (entity [::role-users/role-users])))

(def env {:schema schema :jdbc jdbc-config})

(deftest nested-relations-test
  (testing "joined entities containing only nil values are filtered out
            (happens when there is no remote entities)"
    (mutate! env ::account/create {::account/name  "a3"
                                   ::account/state :active})

    (is (= {::account/name     "a3"}
           (q/execute env
                      [::account/name "a3"]
                      [::account/name {::account/invoices [::invoice/id
                                                           ::invoice/name
                                                           ::invoice/state]}])))))

(deftest insert-account-test
  (testing "cannot retrieve account 3"
    (is (nil? (q/execute env [::account/id 3]
                         [::account/name]))))

  (testing "inserting additional account"
    (mutate! env ::account/create {::account/name  "a3"
                                   ::account/state :active}))

  (testing "can retrieve account 3"
    (is (= {::account/name  "a3"
            ::account/state :active}
           (q/execute env
                      [::account/id 3]
                      [::account/name ::account/state]))))

  (testing "can update account 3"
    (mutate! env ::account/update {::account/id    3
                                   ::account/state :active
                                   ::account/name  "new name"}))

  (testing "can update account 3 with spec-coerce coercion"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"mutation params do not conform to :my.entities.account/update"
         (mutate! env ::account/update {::account/id    3
                                        ::account/name  "new name"
                                        ::account/state "active"}))))

  (testing "throw if an non-existing account is updated"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"the mutation has failed"
         (mutate! env ::account/update {::account/id    987654321
                                        ::account/state :active
                                        ::account/name  "new name"})))))

(deftest store-test
  (let [calls             (atom 0)
        counting-listener (fn [& _] (swap! calls inc))]
    (testing "cannot retrieve account 3"

      (is (nil? (q/execute env [::account/id 3] [::account/name]))))

    (testing "inserting additional account"
      (mutate! env ::account/create {::account/name  "a3"
                                     ::account/state :active}))

    (testing "can retrieve account 3"
      (is (= {::account/name  "a3"
              ::account/state :active}
             (q/execute env [::account/id 3] [::account/name
                                              ::account/state]))))

    (testing "can retrieve roles for account 1"
      (is (= {::role/name "a0r0"}
             (q/execute env [::role/id 0] [::role/name]))))

    (testing "can retrieve user for roles for account 1"
      (is (= {::role/name "a0r0" ::role/users [#::user{:name "u0a0"}
                                               #::user{:name "u1a0"}]}
             (q/execute env [::role/id 0] [::role/name {::role/users [::user/name]}]))))

    (testing "three accounts are active"
      (is (= [{::account/name "a0"} {::account/name "a1"} {::account/name "a3"}]
             (q/execute env ::account/account [::account/name] [[::account/active]]))))

    (testing "get active and suspended accounts"
      (is (= [{::account/name "a0"} {::account/name "a1"} #::account{:name "a2"} {::account/name "a3"}]
             (q/execute env ::account/account [::account/name] [[::account/state :active :suspended]]))))

    (testing "unpaid invoices"
      (is (= [{::invoice/total 2
               ::invoice/lines [{::invoice-line/quantity 1 ::invoice-line/product {::product/id   0
                                                                                   ::product/name "p"}}
                                {::invoice-line/quantity 2 ::invoice-line/product {::product/id   1
                                                                                   ::product/name "q"}}]}]
             (q/execute env ::invoice/invoice
                        [::invoice/total {::invoice/lines [::invoice-line/quantity
                                                           {::invoice-line/product [::product/id
                                                                                    ::product/name]}]}]
                        [[::invoice/unpaid]]))))

    (testing "can register listener and register is called"
      (reset! calls 0)
      (-> env
          (env/update-schema add-listener ::account/create ::counting-listener1 counting-listener)
          (mutate! ::account/create {::account/name  "a3"
                                     ::account/state :active}))
      (is (= 1 @calls)))

    (testing "cannot register listener twice with same key"
      (is (thrown? clojure.lang.ExceptionInfo
                   (-> env
                       (env/update-schema add-listener ::account/create ::counting-listener2 counting-listener)
                       (env/update-schema add-listener ::account/create ::counting-listener2 counting-listener)))))

    (testing "can remove a listener and register is not called anymore"
      (reset! calls 0)
      (-> env
          (env/update-schema add-listener ::account/create ::counting-listener3 counting-listener)
          (env/update-schema remove-listener ::account/create ::counting-listener3)
          (mutate! ::account/create  {::account/name  "a3"
                                      ::account/state :active}))

      (is (zero? @calls)))

    (testing "removing a listener that was not registered doesn't have side-effects"
      (reset! calls 0)
      (-> env
          (env/update-schema add-listener ::account/create ::counting-listener4 counting-listener)
          (env/update-schema remove-listener ::account/create ::not-registered)
          (mutate! ::account/create  {::account/name  "a3"
                                      ::account/state :active}))
      (is (= 1 @calls)))))

(s/def ::user/create ::user/user)
(s/def ::user/update any?)
(deftest mutation-with-precondition
  (let [schema (make-schema
                (entity-from-spec ::user/user
                                  (add-create-mutation)
                                  (add-update-by-id-mutation ::user/id)
                                  (add-precondition :update ::u0a0?
                                                    (fn [user]
                                                      (-> (h/select :*)
                                                          (h/from :user)
                                                          (h/where [:= :name "u0a0"]
                                                                   [:= :id (::user/id user)]))))))
        env (assoc env :schema schema)]

    (testing "we can mutate when initial precondition matches"
      (is (seq (mutate! env ::user/update {::user/id 0
                                           ::user/email "u0@a0"}))))

    (testing "we cannot mutate when initial precondition matches but pre.valid? fails"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"^Precondition"
                            (mutate! (assoc-in env [:schema :my.entities.user :mutations ::user/update
                                                    :pre 0 :valid?] (constantly false))
                                     ::user/update {::user/id 0
                                                    ::user/email "u0@a0"}))))

    (testing "we have a invalid precondition"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"^Precondition"
                            (mutate! env ::user/update {::user/id 1
                                                        ::user/email "u0@a0"}))))))

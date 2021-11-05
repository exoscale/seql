(ns seql.readme-test
  "Reproduces examples provided in the README"
  (:require [seql.query :as q]
            [seql.env :as env]
            [seql.listener :as l]
            [seql.mutation :as m]
            [seql.helpers  :refer [make-schema field add-create-mutation
                                   has-many has-one condition entity entity-from-spec]]
            [db.fixtures   :refer [jdbc-config with-db-fixtures]]
            [clojure.test  :refer [use-fixtures testing deftest is]]
            [clojure.spec.alpha      :as s]))

(use-fixtures :each (with-db-fixtures :small))

(create-ns 'my.entities)
(create-ns 'my.entities.account)
(create-ns 'my.entities.user)
(create-ns 'my.entities.invoice)
(create-ns 'my.entities.invoice-line)
(create-ns 'my.entities.product)
(create-ns 'my.entities.role)

(alias 'account 'my.entities.account)
(alias 'user 'my.entities.user)
(alias 'invoice 'my.entities.invoice)
(alias 'invoice-line 'my.entities.invoice-line)
(alias 'product 'my.entities.product)
(alias 'role 'my.entities.role)

(s/def ::account/name string?)
(s/def ::account/state #{:active :suspended :terminated})
(s/def ::account/account (s/keys :req [::account/name ::account/state]))

(s/def ::user/name string?)
(s/def ::user/email string?)
(s/def ::user/user (s/keys :req [::user/name ::user/email]))

(s/def ::invoice/state keyword?)
(s/def ::invoice/total nat-int?)
(s/def ::invoice/invoice (s/keys :req [::invoice/state ::invoice/total]))

(s/def ::invoice-line/quantity nat-int?)
(s/def ::invoice-line/invoice-line (s/keys :req [::invoice-line/quantity]))

(s/def ::product/name string?)
(s/def ::product/product (s/keys :req [::product/name]))

(s/def ::role/name string?)
(s/def ::role/role (s/keys :req [::role/name]))

(deftest first-schema-test
  (let [schema (make-schema
                (entity ::account/account
                        (field :state)
                        (field :name)))
        env    {:schema schema :jdbc jdbc-config}]

    (testing "there are three accounts"
      (is (= [#::account{:name "a0" :state :active}
              #::account{:name "a1" :state :active}
              #::account{:name "a2" :state :suspended}]
             (q/execute env ::account/account [::account/name ::account/state]))))

    (testing "ID lookups work"
      (is (= #::account{:name "a0" :state :active}
             (q/execute env [::account/id 0] [::account/name ::account/state]))))))

(deftest first-schema-variant-test
  (let [schema (make-schema (entity-from-spec ::account/account))
        env    {:schema schema :jdbc jdbc-config}]

    (testing "there are three accounts"
      (is (= [#::account{:name "a0" :state :active}
              #::account{:name "a1" :state :active}
              #::account{:name "a2" :state :suspended}]
             (q/execute env ::account/account [::account/name ::account/state]))))

    (testing "ID lookups work"
      (is (= #::account{:name "a0" :state :active}
             (q/execute env [::account/id 0] [::account/name ::account/state]))))

    (testing "Field conditions can be applied"
      (is (= [#::account{:name "a0" :state :active}
              #::account{:name "a1" :state :active}]
             (q/execute env ::account/account [::account/name ::account/state] [[::account/state :active]]))))))

(deftest second-schema-test
  (let [schema (make-schema
                (entity-from-spec ::account/account
                                  (condition :active :state :active)))
        env     {:schema schema :jdbc jdbc-config}]
    (testing "ID lookups work"
      (is (= #::account{:name "a0" :state :active}
             (q/execute env [::account/name "a0"] [::account/name ::account/state]))))

    (testing "Static conditions work"
      (is (= [#::account{:name "a0"}
              #::account{:name "a1"}]
             (q/execute env ::account/account [::account/name] [[::account/active]]))))

    (testing "Field conditions work"
      (is (= [#::account{:name "a2"}]
             (q/execute env ::account/account [::account/name] [[::account/state :suspended]]))))))

(deftest third-schema-test
  (let [schema (make-schema
                (entity-from-spec ::account/account
                                  (has-many :users [:id ::user/account-id])
                                  (condition :active :state :active))
                (entity-from-spec ::user/user))
        env {:schema schema :jdbc jdbc-config}]

    (testing "Tree lookups work"
      (is (= [#::account{:name  "a0"
                         :state :active
                         :users [#::user{:name "u0a0" :email "u0@a0"}
                                 #::user{:name "u1a0" :email "u1@a0"}]}
              #::account{:name  "a1"
                         :state :active
                         :users [#::user{:name "u2a1" :email "u2@a1"}
                                 #::user{:name "u3a1" :email "u3@a1"}]}
              #::account{:name "a2" :state :suspended}]
             (q/execute env
                        ::account/account
                        [::account/name
                         ::account/state
                         {::account/users [::user/name ::user/email]}]))))

    (testing "Direct lookups work too"
      (is (= [#::user{:name "u0a0" :email "u0@a0"}
              #::user{:name "u1a0" :email "u1@a0"}
              #::user{:name "u2a1" :email "u2@a1"}
              #::user{:name "u3a1" :email "u3@a1"}]
             (q/execute env ::user/user [::user/name ::user/email]))))))

(deftest fifth-schema-test
  (let [schema (make-schema
                (entity-from-spec ::account/account
                                  (has-many :users    [:id ::user/account-id])
                                  (has-many :invoices [:id ::invoice/account-id])
                                  (condition :active  :state :active))
                (entity-from-spec ::user/user)
                (entity-from-spec ::invoice/invoice
                                  (has-many :lines    [:id ::invoice-line/invoice-id])
                                  (condition :unpaid  :state :unpaid)
                                  (condition :paid    :state :paid))
                (entity-from-spec ::product/product)
                (entity-from-spec [::invoice-line/invoice-line :invoiceline]
                                  (has-one :product [:product-id ::product/id])))
        env {:schema schema :jdbc jdbc-config}]

    (testing "there are three accounts"
      (is (= [{::account/name "a0"} {::account/name "a1"} {::account/name "a2"}]
             (q/execute env ::account/account [::account/name]))))

    (testing "two accounts are active"
      (is (= [{::account/name "a0"} {::account/name "a1"}]
             (q/execute env ::account/account [::account/name] [[::account/active]]))))

    (testing "one account is suspended (adding conditions)"
      (is (= [{::account/name "a2"}]
             (q/execute env ::account/account [::account/name]
                        [[::account/state :suspended]]))))

    (testing "can retrieve account by id"
      (is (= {::account/name "a0"}
             (q/execute env [::account/id 0] [::account/name]))))

    (testing "can retrieve account by id (all fields)"
      (is (= {::account/name "a0" ::account/state :active}
             (q/execute env [::account/id 0]))))

    (testing "can retrieve account by name"
      (is (= {::account/name "a0"}
             (q/execute env [::account/name "a0"] [::account/name]))))

    (testing "can process transforms through coax"
      (is (every? keyword?
                  (map ::invoice/state (q/execute env ::invoice/invoice [::invoice/state])))))

    (testing "can list users in accounts"
      (is (= {::account/name  "a0"
              ::account/users [{::user/name "u0a0"}
                               {::user/name "u1a0"}]}
             (q/execute env
                        [::account/name "a0"]
                        [::account/name
                         {::account/users [::user/name]}]))))

    (testing "can do nested joins to construct result trees"
      (is (= {::account/name     "a1"
              ::account/invoices [{::invoice/id    3
                                   ::invoice/total 4
                                   ::invoice/lines [{::invoice-line/quantity 20
                                                     ::invoice-line/product  {::product/name "z"}}]}]}
             (q/execute env
                        [::account/name "a1"]
                        [::account/name
                         {::account/invoices [::invoice/id
                                              ::invoice/total
                                              {::invoice/lines [::invoice-line/quantity
                                                                {::invoice-line/product [::product/name]}]}]}]))))))

;; To name the mutation
(s/def ::account/create ::account/account)
(s/def ::account/update (s/keys :opt [::account/name ::account/state]))

(deftest sixth-schema-test
  (let [schema (make-schema
                (entity-from-spec ::account/account
                                  (has-many :users    [:id ::user/account-id])
                                  (has-many :invoices [:id ::invoice/account-id])
                                  (condition :active  :state :active)
                                  (add-create-mutation))
                (entity-from-spec ::user/user)
                (entity-from-spec ::invoice/invoice
                                  (has-many :lines    [:id ::invoice-line/invoice-id])
                                  (condition :unpaid  :state :unpaid)
                                  (condition :paid    :state :paid))
                (entity-from-spec ::product/product)
                (entity-from-spec [::invoice-line/invoice-line :invoiceline]
                                  (has-one :product [:product-id ::product/id])))
        env {:schema schema :jdbc jdbc-config}]

    (testing "joined entities containing only nil values are filtered out
            (happens when there is no remote entities)"
      (m/mutate! env ::account/create {::account/name  "a3"
                                       ::account/state :active})

      (is (= {::account/state :active}
             (q/execute env [::account/name "a3"] [::account/state])))

      (is (= {::account/name "a3"}
             (q/execute env
                        [::account/name "a3"]
                        [::account/name {::account/invoices [::invoice/id
                                                             ::invoice/state]}])))

      (let [last-result  (atom nil)
            store-result (fn [details]
                           (reset! last-result
                                   (select-keys details [:mutation :result])))
            env          (env/update-schema env
                                            l/add-listener
                                            ::account/create
                                            ::handler
                                            store-result)]

        (m/mutate! env ::account/create {::account/name "a4" ::account/state :active})

        (is (= {:mutation ::account/create
                :result   [{:next.jdbc/update-count 1}]}
               @last-result))))))

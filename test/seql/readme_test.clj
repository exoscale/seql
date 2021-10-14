(ns seql.readme-test
  "Reproduces examples provided in the README"
  (:require [seql.core     :refer [query mutate! add-listener!]]
            [seql.helpers  :refer [make-schema ident field compound mutation
                                   has-many has-one condition entity transform]]
            [db.fixtures   :refer [jdbc-config with-db-fixtures]]
            [clojure.test  :refer [use-fixtures testing deftest is]]
            [clojure.spec.alpha      :as s]
            [honey.sql.helpers       :as h]))

(use-fixtures :each (with-db-fixtures :small))

(s/def :account/name string?)
(s/def :account/state keyword?)
(s/def ::account (s/keys :req [:account/name :account/state]))

(s/def :invoice/state keyword?)

(deftest first-schema-test
  (let [schema (make-schema
                (entity :account
                        (field :id (ident))
                        (field :state)
                        (field :name)))
        env    {:schema schema :jdbc jdbc-config}]

    (testing "there are three accounts"
      (is (= [#:account{:name "a0" :state :active}
              #:account{:name "a1" :state :active}
              #:account{:name "a2" :state :suspended}]
             (query env :account [:account/name :account/state]))))

    (testing "ID lookups work"
      (is (= #:account{:name "a0" :state :active}
             (query env [:account/id 0] [:account/name :account/state]))))))

(deftest second-schema-test
  (let [schema (make-schema
                (entity :account
                        (field :id (ident))
                        (field :name (ident))
                        (field :state)
                        (condition :active :state :active)
                        (condition :state)))
        env     {:schema schema :jdbc jdbc-config}]
    (testing "ID lookups work"
      (is (= #:account{:name "a0" :state :active}
             (query env [:account/name "a0"] [:account/name :account/state]))))

    (testing "Static conditions work"
      (is (= [#:account{:name "a0"}
              #:account{:name "a1"}]
             (query env :account [:account/name] [[:account/active]]))))

    (testing "Field conditions work"
      (is (= [#:account{:name "a2"}]
             (query env :account [:account/name] [[:account/state :suspended]]))))))

(deftest third-schema-test
  (let [schema (make-schema
                (entity :account
                        (field :id (ident))
                        (field :name (ident))
                        (field :state)
                        (has-many :users [:id :user/account-id])
                        (condition :active :state :active)
                        (condition :state))

                (entity :user
                        (field :id (ident))
                        (field :name (ident))
                        (field :email)))
        env {:schema schema :jdbc jdbc-config}]

    (testing "Tree lookups work"
      (is (= [#:account{:name  "a0"
                        :state :active
                        :users [#:user{:name "u0a0" :email "u0@a0"}
                                #:user{:name "u1a0" :email "u1@a0"}]}
              #:account{:name  "a1"
                        :state :active
                        :users [#:user{:name "u2a1" :email "u2@a1"}
                                #:user{:name "u3a1" :email "u3@a1"}]}
              #:account{:name "a2" :state :suspended :users []}]
             (query env
                    :account
                    [:account/name
                     :account/state
                     {:account/users [:user/name :user/email]}]))))

    (testing "Direct lookups work too"
      (is (= [#:user{:name "u0a0" :email "u0@a0"}
              #:user{:name "u1a0" :email "u1@a0"}
              #:user{:name "u2a1" :email "u2@a1"}
              #:user{:name "u3a1" :email "u3@a1"}]
             (query env :user [:user/name :user/email]))))))

(deftest fourth-schema-test
  (let [schema (make-schema
                (entity :invoice
                        (field :id (ident))
                        (field :state)
                        (field :total)
                        (compound :paid? [state] (= state :paid))
                        (condition :paid :state :paid)
                        (condition :unpaid :state :unpaid)))
        env    {:schema schema :jdbc jdbc-config}]

    (testing "Compounds are realized"
      (is (= [#:invoice{:total 2, :paid? false}
              #:invoice{:total 2, :paid? true}
              #:invoice{:total 4, :paid? true}]
             (query env :invoice [:invoice/total :invoice/paid?]))))))

(deftest fifth-schema-test
  (let [schema (make-schema
                (entity :account
                        (field :id          (ident))
                        (field :name        (ident))
                        (field :state)
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
                        (field :state)
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
                        (field :quantity)))
        env {:schema schema :jdbc jdbc-config}]

    (testing "there are three accounts"
      (is (= [{:account/name "a0"} {:account/name "a1"} {:account/name "a2"}]
             (query env :account [:account/name]))))

    (testing "two accounts are active"
      (is (= [{:account/name "a0"} {:account/name "a1"}]
             (query env :account [:account/name] [[:account/active]]))))

    (testing "one account is suspended (adding conditions)"
      (is (= [{:account/name "a2"}]
             (query env :account [:account/name]
                    [[:account/state :suspended]]))))

    (testing "can retrieve account by id"
      (is (= {:account/name "a0"}
             (query env [:account/id 0] [:account/name]))))

    (testing "can retrieve account by id (all fields)"
      (is (= {:account/name "a0" :account/id 0 :account/state :active}
             (query env [:account/id 0]))))

    (testing "can retrieve account by name"
      (is (= {:account/name "a0"}
             (query env [:account/name "a0"] [:account/name]))))

    (testing "can process transforms"
      (is (every? keyword?
                  (map :invoice/state (query env :invoice [:invoice/state])))))

    (testing "can produce compound fields"
      (is (= {:invoice/paid? true}
             (query env [:invoice/id 1] [:invoice/paid?]))))

    (testing "can list users in accounts"
      (is (= {:account/name  "a0"
              :account/users [{:user/name "u0a0"}
                              {:user/name "u1a0"}]}
             (query env
                    [:account/name "a0"]
                    [:account/name
                     {:account/users [:user/name]}]))))

    (testing "can do nested joins to construct result trees"
      (is (= {:account/name     "a1"
              :account/invoices [{:invoice/id    3
                                  :invoice/total 4
                                  :invoice/lines [{:line/quantity 20
                                                   :line/product  {:product/name "z"}}]}]}
             (query env
                    [:account/name "a1"]
                    [:account/name
                     {:account/invoices [:invoice/id
                                         :invoice/total
                                         {:invoice/lines [:line/quantity
                                                          {:line/product [:product/name]}]}]}]))))))

(deftest sixth-schema-test
  (let [schema (make-schema
                (entity :account
                        (field :id          (ident))
                        (field :name        (ident))
                        (field :state       (transform :keyword))

                        (has-many :users    [:id :user/account-id])
                        (has-many :invoices [:id :invoice/account-id])

                        (condition :active  :state :active)
                        (condition :state)

                        (mutation :account/create
                                  ::account
                                  [params]
                                  (-> (h/insert-into :account)
                                      (h/values [params])))

                        (mutation :account/update
                                  ::account
                                  [{:keys [id] :as params}]
                                  (-> (h/update :account)
                                      ;; values are fed unqualified
                                      (h/set (dissoc params :id))
                                      (h/where [:= :id id]))))
                (entity :user
                        (field :id          (ident))
                        (field :name        (ident))
                        (field :email))
                (entity :invoice
                        (field :id          (ident))
                        (field :state)
                        (field :total)
                        (compound :paid?    [state] (= state :paid))
                        (has-many :lines    [:id :line/invoice-id])

                        (condition :unpaid  :state :unpaid)
                        (condition :paid    :state :paid))
                (entity [:line :invoiceline]
                        (field :id          (ident))
                        (field :product)
                        (field :quantity)))
        env {:schema schema :jdbc jdbc-config}]

    (testing "joined entities containing only nil values are filtered out
            (happens when there is no remote entities)"
      (mutate! env :account/create {:account/name  "a3"
                                    :account/state :active})

      (is (= {:account/state :active}
             (query env [:account/name "a3"] [:account/state])))

      (is (= {:account/name     "a3"
              :account/invoices []}
             (query env
                    [:account/name "a3"]
                    [:account/name {:account/invoices [:invoice/id
                                                       :invoice/state]}])))

      (let [last-result  (atom nil)
            store-result (fn [details]
                           (reset! last-result
                                   (select-keys details [:mutation :result])))
            env          (add-listener! env
                                        :account/create
                                        ::handler
                                        store-result)]

        (mutate! env :account/create {:account/name "a4" :account/state :active})

        (is (= {:mutation :account/create
                :result   [{:next.jdbc/update-count 1}]}
               @last-result))))))

(ns seql.integration-test
  (:require [seql.core          :refer [query mutate!
                                        add-listener! remove-listener!]]
            [seql.helpers       :refer [make-schema ident field compound
                                        mutation transform has-many has-one condition
                                        has-many-through
                                        entity]]
            [db.fixtures        :refer [jdbc-config with-db-fixtures]]
            [clojure.test       :refer [use-fixtures testing deftest is]]
            [clojure.spec.alpha :as s]
            [honeysql.helpers   :as h]))

(use-fixtures :each (with-db-fixtures :small))

(s/def :account/name string?)
(s/def :account/state keyword?)
(s/def ::account (s/keys :req [:account/name :account/state]))

(def schema
  "As gradually explained in the project's README"
  (make-schema
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
                     [{:keys [:account/id] :as params}]
                     (-> (h/update :account)
                         (h/sset (dissoc params :account/id))
                         (h/where [:= :id id]))))
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
           (field :quantity))
   (entity [:role :user-role]
           (field :id (ident))
           (field :name)
           (has-many-through :users [:id
                                     :role-users/role-id
                                     :role-users/user-id
                                     :user/id]))))

(def env {:schema schema :jdbc jdbc-config})

(deftest nested-relations-test
  (testing "joined entities containing only nil values are filtered out
            (happens when there is no remote entities)"
    (mutate! env :account/create {:account/name  "a3"
                                  :account/state :active})

    (is (= {:account/name     "a3"
            :account/invoices []}
           (query env
                  [:account/name "a3"]
                  [:account/name {:account/invoices [:invoice/id
                                                     :invoice/state]}])))))

(deftest insert-account-test
  (testing "cannot retrieve account 3"
    (is (nil? (query env [:account/id 3]
                     [:account/name]))))

  (testing "inserting additional account"
    (mutate! env :account/create {:account/name  "a3"
                                  :account/state :active}))

  (testing "can retrieve account 3"
    (is (= {:account/name  "a3"
            :account/state :active}
           (query env
                  [:account/id 3]
                  [:account/name :account/state]))))

  (testing "can update account 3"
    (mutate! env :account/update {:account/id    3
                                  :account/state :active
                                  :account/name  "new name"}))

  (testing "can update account 3 with spec-coerce coercion"
    (mutate! env :account/update {:account/id    3
                                  :account/state "active"}))

  (testing "throw if an non-existing account is updated"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"the mutation has failed"
         (mutate! env :account/update {:account/id    987654321
                                       :account/state :active
                                       :account/name  "new name"})))))

(deftest store-test
  (let [calls             (atom 0)
        counting-listener (fn [& _] (swap! calls inc))
        store             (atom env)]
    (testing "cannot retrieve account 3"

      (is (nil? (query @store [:account/id 3] [:account/name]))))

    (testing "inserting additional account"
      (mutate! @store :account/create {:account/name  "a3"
                                       :account/state :active}))

    (testing "can retrieve account 3"
      (is (= {:account/name  "a3"
              :account/state :active}
             (query @store [:account/id 3] [:account/name :account/state]))))

    (testing "can retrieve roles for account 1"
      (is (= {:role/name "a0r0"}
             (query @store [:role/id 0] [:role/name]))))

    (testing "can retrieve user for roles for account 1"
      (is (= {:role/name "a0r0" :role/users [#:user{:name "u0a0"}
                                             #:user{:name "u1a0"}]}
             (query @store [:role/id 0] [:role/name {:role/users [:user/name]}]))))

    (testing "two accounts are active"
      (is (= [{:account/name "a0"} {:account/name "a1"} {:account/name "a3"}]
             (query @store :account [:account/name] [[:account/active]]))))

    (testing "unpaid invoices"
      (is (= [{:invoice/total 2
               :invoice/lines [{:line/quantity 1 :line/product {:product/id 0 :product/name "p"}}
                               {:line/quantity 2 :line/product {:product/id 1 :product/name "q"}}]}]
             (query @store :invoice
                    [:invoice/total {:invoice/lines [:line/quantity {:line/product [:product/id :product/name]}]}]
                    [[:invoice/unpaid]]))))

    (testing "can register listener and register is called"
      (reset! calls 0)
      (swap! store add-listener! :account/create ::counting-listener1 counting-listener)
      (mutate! @store :account/create {:account/name  "a3"
                                       :account/state :active})
      (swap! store remove-listener! :account/create ::counting-listener1)
      (is (= 1 @calls)))

    (testing "cannot register listener twice with same key"
      (reset! calls 0)
      (is (thrown? clojure.lang.ExceptionInfo
                   (do
                     (swap! store add-listener! :account/create ::counting-listener2 counting-listener)
                     (swap! store add-listener! :account/create ::counting-listener2 counting-listener))))
      (swap! store remove-listener! :account/create ::counting-listener2))

    (testing "can remove a listener and register is not called anymore"
      (reset! calls 0)
      (swap! store add-listener!    :account/create ::counting-listener3 counting-listener)
      (swap! store remove-listener! :account/create ::counting-listener3)
      (mutate! @store :account/create {:account/name  "a3"
                                       :account/state :active})

      (is (zero? @calls)))

    (testing "removing a listener that was not registered doesn't have side-effects"
      (reset! calls 0)
      (swap! store add-listener!    :account/create ::counting-listener4 counting-listener)
      (swap! store remove-listener! :account/create ::not-registered)
      (mutate! @store :account/create {:account/name  "a3"
                                       :account/state :active})

      (is (= 1 @calls)))))


(deftest mutation-with-precondition
  (let [schema {:user
                {:entity :user
                 :table :user
                 :idents [:user/id]
                 :fields [:user/id
                          :user/name
                          :user/email
                          :user/account-id]
                 :mutations {:user/create {:spec any?
                                           :handler (fn [params]
                                                      (-> (h/insert-into :user)
                                                          (h/values [params])))}
                             :user/update {:spec any?
                                           :pre [{:name ::u0a0?
                                                  :query (fn [user]
                                                           (-> (h/select :*)
                                                               (h/from :user)
                                                               (h/where [:= :name "u0a0"]
                                                                        [:= :id (:user/id user)])))}]
                                           :handler
                                           (fn [{:keys [:user/id] :as params}]
                                             (-> (h/update :user)
                                                 (h/sset (dissoc params :user/id))
                                                 (h/where [:= :id id])))}}}}
        env (assoc env :schema schema)]

    (testing "we can mutate when initial precondition matches"
      (is (seq (mutate! env :user/update {:user/id 0
                                          :user/email "u0@a0"}))))

    (testing "we cannot mutate when initial precondition matches but pre.valid? fails"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"^Precondition"
                            (mutate! (assoc-in env [:schema :user :mutations :user/update
                                                    :pre 0 :valid?] (constantly false))
                                     :user/update {:user/id 0
                                                   :user/email "u0@a0"}))))

    (testing "we have a invalid precondition"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"^Precondition"
                            (mutate! env :user/update {:user/id 1
                                                       :user/email "u0@a0"}))))))

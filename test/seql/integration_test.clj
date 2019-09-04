(ns seql.integration-test
  (:require [seql.core          :refer [query mutate!
                                        add-listener! remove-listener!]]
            [seql.helpers       :refer [make-schema ident field compound
                                        mutation transform has-many condition
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
                     [{:keys [id] :as params}]
                     (-> (h/update :account)
                         ;; values are fed unqualified
                         (h/sset (dissoc params :id))
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
   (entity [:line :invoiceline]
           (field :id          (ident))
           (field :product)
           (field :quantity))))

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

    (testing "two accounts are active"
      (is (= [{:account/name "a0"} {:account/name "a1"} {:account/name "a3"}]
             (query @store :account [:account/name] [[:account/active]]))))

    (testing "can register listener and register is called"
      (reset! calls 0)
      (swap! store add-listener! :account/create counting-listener)
      (mutate! @store :account/create {:account/name  "a3"
                                       :account/state :active})
      (is (= 1 @calls)))


    (testing "can remove a listener and register is not called anymore"
      (reset! calls 0)
      (swap! store add-listener!    :account/create counting-listener)
      (swap! store remove-listener! :account/create counting-listener)
      (mutate! @store :account/create {:account/name  "a3"
                                       :account/state :active})

      (is (zero? @calls)))

  (testing "removing a listener that was not registered doesn't have side-effects"
    (let [listener2 (constantly nil)]
      (reset! calls 0)
      (swap! store add-listener!    :account/create counting-listener)
      (swap! store remove-listener! :account/create listener2)
      (mutate! @store :account/create {:account/name  "a3"
                                       :account/state :active})

      (is (= 1 @calls))))))

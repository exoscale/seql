(ns seql.helpers-schema-test
  (:require [seql.helpers :refer :all]
            [clojure.test :refer :all]))

(def built-schema
  (make-schema
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

   (entity [:line :invoiceline]
           (field :id          (ident))
           (field :product)
           (field :quantity))))

(def raw-schema
  {:account {:table      :account
             :idents     [:account/id :account/name]
             :fields     [:account/id :account/name :account/state]
             :conditions {:account/active {:type  :static
                                           :field :account/state
                                           :value :active}
                          :account/state  {:type  :field
                                           :field :account/state}}
             :relations  {:account/invoices {:type          :one-to-many
                                             :remote-entity :invoice
                                             :local-id      :account/id
                                             :remote-id     :invoice/account-id}
                          :account/users    {:type          :one-to-many
                                             :remote-entity :user
                                             :local-id      :account/id
                                             :remote-id     :user/account-id}}}
   :invoice {:table      :invoice
             :idents     [:invoice/id]
             :fields     [:invoice/id :invoice/state :invoice/total]
             :conditions {:invoice/unpaid {:type  :static
                                           :field :invoice/state
                                           :value :unpaid}
                          :invoice/paid   {:type  :static
                                           :field :invoice/state
                                           :value :paid}}
             :compounds  {:invoice/paid? {:source  [:invoice/state]
                                          :handler (partial = :paid)}}
             :relations  {:invoice/lines {:type          :one-to-many
                                          :remote-entity :line
                                          :local-id      :invoice/id
                                          :remote-id     :line/invoice-id}}}
   :line    {:table  :invoiceline
             :idents [:line/id]
             :fields [:line/id :line/product :line/quantity]}
   :user    {:table  :user
             :idents [:user/id :user/name]
             :fields [:user/id :user/name :user/email]}})

(def raw-schema-without-compound
  (update raw-schema :invoice dissoc :compounds))

(def built-schema-without-compound
  (update built-schema :invoice dissoc :compounds))

(deftest deterministic-schema-builds
  (testing "schema equality"
    (is (= raw-schema-without-compound built-schema-without-compound)))

  (testing "feature parity for compounds"
    (let [raw-compound   (get-in raw-schema [:invoice :compounds :invoice/paid? :handler])
          built-compound (get-in built-schema [:invoice :compounds :invoice/paid? :handler])]
      (is (= (raw-compound :paid) true))
      (is (= (built-compound :paid) true))
      (is (= (raw-compound :unpaid) false))
      (is (= (built-compound :unpaid) false))
      (is (= (raw-compound nil) false))
      (is (= (built-compound nil) false)))))

(deftest bad-helper-usage

  (testing "misplaced use of helpers throws exceptions"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"The ident form should be used inside field definitions"
         (make-schema (ident))))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"The ident form should be used inside field definitions"
         (entity :example (ident))))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"The field form should be used inside entity definitions"
         (make-schema (field :id))))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Unknown form encountered"
         (entity :example (make-schema))))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Unknown form encountered"
         (entity :example {})))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"The compound form should be used inside entity definitions"
         (make-schema
          (compound :paid? [state] (= state :paid)))))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"The one-to-many form should be used inside entity definitions"
         (make-schema
          (has-many :users [:id :user/account-id]))))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"The field-condition form should be used inside entity definitions"
         (make-schema
          (condition :user))))))

(ns seql.helpers-schema-test
  (:require [seql.helpers :refer [condition entity field entity-from-spec has-many make-schema]]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest testing is]]))

(def built-schema
  (make-schema
   (entity :account
           (field :id)
           (field :name)
           (field :state)
           (has-many :users    [:id :user/account-id])
           (has-many :invoices [:id :invoice/account-id])
           (condition :active  :state :active))

   (entity :user
           (field :id)
           (field :name)
           (field :email))

   (entity :invoice
           (field :id)
           (field :state)
           (field :total)
           (has-many :lines    [:id :line/invoice-id])

           (condition :unpaid  :state :unpaid)
           (condition :paid    :state :paid))

   (entity [:line :invoiceline]
           (field :id)
           (field :product)
           (field :quantity))))

(def raw-schema
  {:account {:table      :account
             :fields     [:account/id :account/name :account/state]
             :conditions {:account/active {:type  :static
                                           :field :account/state
                                           :value :active}}
             :relations  {:account/invoices {:type          :one-to-many
                                             :remote-entity :invoice
                                             :local-id      :account/id
                                             :remote-id     :invoice/account-id}
                          :account/users    {:type          :one-to-many
                                             :remote-entity :user
                                             :local-id      :account/id
                                             :remote-id     :user/account-id}}}
   :invoice {:table      :invoice
             :fields     [:invoice/id :invoice/state :invoice/total]
             :conditions {:invoice/unpaid {:type  :static
                                           :field :invoice/state
                                           :value :unpaid}
                          :invoice/paid   {:type  :static
                                           :field :invoice/state
                                           :value :paid}}
             :relations  {:invoice/lines {:type          :one-to-many
                                          :remote-entity :line
                                          :local-id      :invoice/id
                                          :remote-id     :line/invoice-id}}}
   :line    {:table  :invoiceline
             :fields [:line/id :line/product :line/quantity]}
   :user    {:table  :user
             :fields [:user/id :user/name :user/email]}})

(deftest deterministic-schema-builds
  (testing "schema equality"
    (is (= raw-schema built-schema))))

(deftest bad-helper-usage

  (testing "misplaced use of helpers throws exceptions"

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
         #"The one-to-many form should be used inside entity definitions"
         (make-schema
          (has-many :users [:id :user/account-id]))))

    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"The static-condition form should be used inside entity definitions"
         (make-schema
          (condition :paid :state :paid))))))

(s/def ::sub1 (s/keys :req [::a] :opt [::b :account/id] :opt-un [::nope]))
(s/def ::sub2 (s/keys :req [::c :user/id] :opt [::d ::b]))
(s/def ::merged (s/merge ::sub1 ::sub2))
(s/def ::alias ::merged)

(deftest keys-resolution-test
  (testing "keys resolution"
    (is (= #{::a ::b ::c ::d}
           (set (:fields (entity-from-spec ::alias)))))))

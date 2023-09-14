(ns seql.multi-query-integration-test
  (:require [seql.mutation :refer [mutate!]]
            [clojure.spec.alpha :as s]
            [seql.env :as env]
            [seql.query    :as q]
            [seql.helpers       :refer [make-schema entity-from-spec mutation-fn]]
            [db.fixtures        :refer [jdbc-config with-db-fixtures]]
            [clojure.test       :refer [use-fixtures testing deftest is]]))

(use-fixtures :each (with-db-fixtures :small))

(create-ns 'my.entities)
(create-ns 'my.entities.account)
(alias 'account 'my.entities.account)

(s/def ::account/name string?)
(s/def ::account/id nat-int?)
(s/def ::account/state #{:active :suspended :terminated})
(s/def ::account/account (s/keys :req [::account/name ::account/state]))
(s/def ::account/create ::account/account)

(def schema
  "As gradually explained in the project's README"
  (make-schema
   (entity-from-spec ::account/account
                     (mutation-fn ::account/delete-two any?
                                  (constantly
                                   [{:delete-from :account :where [:= :id 1]}
                                    {:delete-from :account :where [:= :id 2]}]))
                     (mutation-fn ::account/add-two any?
                                  (constantly
                                   [{:insert-into :account
                                     :columns [:id :name :state]
                                     :values [[1 "hello" "active"]]}
                                    {:insert-into :account
                                     :columns [:id :name :state]
                                     :values [[2 "bye" "suspended"]]}])))))

(def env
  (env/make-env jdbc-config schema))

(deftest multi-query-test
  (testing "adding two new accounts via a multi query mutation"
    (mutate! env ::account/add-two {}))

  (testing "retrieving newly created account by name"
    (let [{::account/keys [state]} (q/execute env [::account/name "hello"])]
      (is (= state :active)))
    (let [{::account/keys [state]} (q/execute env [::account/name "bye"])]
      (is (= state :suspended))))

  (mutate! env ::account/delete-two {}))

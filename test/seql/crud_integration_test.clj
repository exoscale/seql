(ns seql.crud-integration-test
  (:require [seql.mutation :refer [mutate!]]
            [clojure.spec.alpha :as s]
            [seql.env :as env]
            [seql.query    :as q]
            [seql.helpers       :refer [make-schema entity-from-spec add-create-mutation
                                        add-update-by-id-mutation add-delete-by-id-mutation]]
            [db.fixtures        :refer [jdbc-config with-db-fixtures]]
            [clojure.test       :refer [use-fixtures testing deftest is]]))

(use-fixtures :each (with-db-fixtures :small))

(create-ns 'my.entities)
(create-ns 'my.entities.account)
(alias 'account 'my.entities.account)

(s/def ::account/name string?)
(s/def ::account/id nat-int?)
(s/def ::account/state #{:active :suspended :terminated})
(s/def ::account/account (s/keys :opt [::account/id] :req [::account/name ::account/state]))
(s/def ::account/create ::account/account)
(s/def ::account/update (s/keys :opt [::account/name ::account/state]))
(s/def ::account/delete (s/keys :req [::account/id]))

(def schema
  "As gradually explained in the project's README"
  (make-schema
   (entity-from-spec ::account/account
                     (add-create-mutation)
                     (add-update-by-id-mutation ::account/id)
                     (add-delete-by-id-mutation ::account/id))))

(def env
  (env/make-env jdbc-config schema))

(deftest crud-test
  (let [stored-id (atom nil)]
    (testing "adding a new account"
      (mutate! env ::account/create #::account{:name "foo" :state :active}))

    (testing "retrieving newly created account by name"
      (let [{::account/keys [id state] :as account} (q/execute env [::account/name "foo"])]
        (is (some? account))
        (is (= state :active))
        (is (pos? id))
        (reset! stored-id id)))

    (testing "updating account state"
      (when (some? @stored-id)
        (mutate! env ::account/update #::account{:id @stored-id :state :suspended}))
      (is (= #::account{:id @stored-id :state :suspended :name "foo"}
             (q/execute env [::account/name "foo"]))))

    (testing "deleting account"
      (when (some? @stored-id)
        (mutate! env ::account/delete {::account/id @stored-id}))
      (is (nil? (q/execute env [::account/name "foo"]))))))

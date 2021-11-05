(ns sandbox
  "An example namespace to exercise SEQL queries"
  (:require [db.fixtures  :refer [load-fixtures jdbc-config]]
            [clojure.spec.alpha :as s]
            [seql.query :as q]
            [seql.mutation :as m :refer [mutate!]]
            [seql.helpers :refer [make-schema entity-from-spec has-many add-precondition
                                  add-create-mutation add-update-by-id-mutation
                                  add-delete-by-id-mutation]]))

(create-ns 'exoscale.entity.account)
(create-ns 'exoscale.entity.user)
(alias 'account 'exoscale.entity.account)
(alias 'user 'exoscale.entity.user)

(comment

  ;; Define a few specs for entities
  (do
    (s/def ::account/id nat-int?)
    (s/def ::account/name string?)
    (s/def ::account/state #{:active :suspended :terminated})
    (s/def ::account/account (s/keys :opt [::account/id] :req [::account/name ::account/state]))
    (s/def ::account/create ::account/account)
    (s/def ::account/update (s/keys :opt [::account/name ::account/state]))
    (s/def ::account/delete (s/keys :req [::account/id]))
    (s/def ::user/name string?)
    (s/def ::user/email string?)
    (s/def ::user/user (s/keys :req [::user/name ::user/email])))

  ;; Load the provided entity set
  (load-fixtures :small)

  ;; Set an environment up
  (def env
    {:jdbc   jdbc-config
     :schema (make-schema
              (entity-from-spec
               ::account/account
               (has-many :users [:id ::user/account-id])
               (add-create-mutation)
               (add-update-by-id-mutation ::account/id)
               (add-delete-by-id-mutation ::account/id)
               (add-precondition :update ::fail (constantly
                                                 {:select [:id]
                                                  :from [:account]
                                                  :where [:= :id 1000]})))
              (entity-from-spec ::user/user))})

  ;; Perform several mutations in a transaction
  (m/with-transaction env
    (mutate! env ::account/create #::account{:name "a5" :state :suspended})
    (mutate! env ::account/create #::account{:name "a5" :state :suspended}))

  (mutate! env ::account/create #::account{:name "a3" :state :suspended})
  (mutate! env ::account/update #::account{:id 3 :name "a3" :state :active})
  (q/execute env [::account/name "a3"])
  (q/execute env [::account/name "a5"])

  (mutate! env ::account/delete {::account/id 3}))

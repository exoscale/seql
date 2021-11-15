(ns sandbox
  "An example namespace to exercise SEQL queries"
  (:require [db.fixtures  :refer [load-fixtures jdbc-config]]
            [clojure.spec.alpha :as s]
            [seql.query :as q]
            [seql.mutation :as m :refer [mutate!]]
            [seql.helpers :refer [make-schema entity-from-spec
                                  add-create-mutation add-update-by-id-mutation
                                  add-delete-by-id-mutation]]))

;; Define a few specs for entities
(s/def :account/id nat-int?)
(s/def :account/name string?)
(s/def :account/state #{:active :suspended :terminated})
(s/def :account/account (s/keys :opt [:account/id] :req [:account/name :account/state]))

;; Mutation specs
(s/def :account/create :account/account)
(s/def :account/update (s/keys :opt [:account/name :account/state]))
(s/def :account/delete (s/keys :req [:account/id]))

;; Set an environment up
(def env
  {:jdbc   jdbc-config
   :schema (make-schema
            (entity-from-spec
             :account/account
             (add-create-mutation)
             (add-update-by-id-mutation :account/id)
             (add-delete-by-id-mutation :account/id)))})

(comment
  ;; Load the provided entity set
  (load-fixtures :small)

  ;; Perform several mutations in a transaction
  (m/with-transaction env
    (mutate! env :account/create #:account{:name "a3" :state :suspended})
    (mutate! env :account/create #:account{:name "a4" :state :suspended}))

  (mutate! env :account/delete {:account/id 3})

  (q/execute env :account/account)
  ;; Have fun!
  )

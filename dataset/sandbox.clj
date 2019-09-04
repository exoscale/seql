(ns sandbox
  "An example namespace to exercise SEQL queries"
  (:require [db.fixtures  :refer [load-fixtures jdbc-config]]
            [seql.core    :refer [query mutate!]]
            [seql.helpers :refer :all]))

(comment

  ;; Load the provided entity set
  (load-fixtures :small)

  ;; Set an environment up
  (def env
    {:jdbc   jdbc-config
     :schema (make-schema
              (entity :account
                      (field :id (ident))
                      (field :name (ident))
                      (field :state (transform :keyword))))})

  ;; Perform queries
  (query env :account [:account/name :account/state])
  )

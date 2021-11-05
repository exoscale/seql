(ns seql.core
  "Namespace kept for backward compatibility"
  (:require [seql.query    :as query]
            [seql.env      :as env]
            [seql.mutation :as mutation]
            [seql.listener :as listener]))

(def ^:deprecated query
  "use `seql.query/execute`"
  #'query/execute)

(def ^:deprecated mutate!
  "use `seql.mutation/mutate!`"
  #'mutation/mutate!)

(defn ^:deprecated add-listener!
  "use `seql.listener/add-listener`"
  ([env mutation handler]
   (env/update-schema env listener/add-listener mutation mutation handler))
  ([env mutation key handler]
   (env/update-schema env listener/add-listener mutation key handler)))

(defn ^:deprecated remove-listener!
  "use `seql.listener/remove-listener`"
  ([env mutation]
   (env/update-schema env listener/remove-listener mutation mutation))
  ([env mutation key]
   (env/update-schema env listener/remove-listener mutation key)))

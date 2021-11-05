(ns seql.listener
  "Facilities to register and run listeners. Listeners are
   ran after a successful mutation has happened and are fed
   an identifier, the transaction results as well as its initial
   pameters."
  (:require [seql.schema :as schema]))

(defn add-listener
  "Given an environment, add a mutation handler.
  The handlers is bound by `key`, if specified, otherwise the `key` will
  default to the mutation key. Yields an updated environment"
  ([schema mutation handler]
   (add-listener schema mutation mutation handler))
  ([schema mutation key handler]
   ;; Throw early for unknown mutations
   (schema/resolve-mutation schema mutation)
   (if-let [_ (schema/resolve-listener schema mutation key)]
     (throw (ex-info (str "Listener already registered for: " key)
                     {:type ::already-registered-error
                      :key key}))
     (schema/set-listener schema mutation key handler))))

(defn remove-listener
  "Given an environment, remove a mutation handler by `key` if
  specified, otherwise it will remove a handler that match the
  mutation `key`. Yields an updated environment."
  ([schema mutation]
   (remove-listener schema mutation mutation))
  ([schema mutation key]
   ;; Throw early for unknown mutations
   (schema/resolve-mutation schema mutation)
   (schema/remove-listener schema mutation key)))

(defn notify!
  "Run through the list of registered listeners for a mutation"
  [schema mutation args]
  (let [listeners (schema/resolve-listeners schema mutation)]
    (run! (fn [[key listener]]
            (listener (assoc args :key key :mutation mutation)))
          listeners)))

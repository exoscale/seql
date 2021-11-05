(ns seql.env
  "Facilities to manipulate seql environments")

(defrecord ^:no-doc Environment [jdbc schema])

(def schema
  "Retrieve the schema from an environment"
  :schema)

(def jdbc
  "Retrieve jdbc handle from an environment"
  :jdbc)

(defn set-jdbc
  "Update an environment with a new connectable source.
   This can be useful to run multiple queries in a transaction."
  [env new-jdbc]
  (->Environment new-jdbc (schema env)))

(defn update-schema
  "Update the environment's schema by applying f and args to it as for `update`."
  [env f & args]
  (->Environment (jdbc env) (apply f (schema env) args)))

(def make-env
  "Build an environment"
  #'->Environment)

(def environment
  "An empty environment, can be used with wiring libraries to feed
   dependencies."
  (map->Environment {}))

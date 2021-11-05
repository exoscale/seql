(ns ^:no-doc seql.schema
  "Facilities to peek inside the schema definition.
  No assumptions should be made on the shape of the schema  outside
  of this namespace."
  (:require [camel-snake-kebab.core :refer [->snake_case_string
                                            ->snake_case_keyword
                                            ->kebab-case-string]]
            [clojure.string :as str]))

(defn resolve-entity
  "Every seql query targets an entity, given as the second
   argument to `seql.query/execute`. When listing records,
   A keyword is expected which will resolve to the entity's
   namespace.

   When a qualified keyword is given, it's namespace is looked
   up, a simple keyword is assumed to be the namespace.

   To query a single record by one of its ident, a collection
   of two arguments can be given: the ident and its value. In
   this case, the namespace of the ident will be assumed to
   be the entity's namespace."
  [kw]
  (cond
    (coll? kw)              (-> kw first namespace keyword)
    (qualified-keyword? kw) (-> kw namespace keyword)
    (keyword? kw)           kw
    :else                   (throw (ex-info "bad entity specification" {:entity kw}))))

(defn- resolve-by-entity
  "A convenience to access an entity's schema"
  [schema kw & paths]
  (get-in schema (concat [(resolve-entity kw)] paths)))

(defn resolve-mutation
  "Mutations in seql are qualified keywords, return the mutation config as stored
   in the schema or fail forcibly"
  [schema kw]
  (or (resolve-by-entity schema kw :mutations kw)
      (throw (ex-info (str "unknown mutation: " kw)
                      {:type     :error/illegal-argument
                       :code     400
                       :mutation kw}))))

(defn resolve-listener
  "Listeners in seql are handlers to run once a mutation has successfully completed.
   Each should have a unique keyword key."
  [schema mutation kw]
  (resolve-by-entity schema mutation :listeners mutation kw))

(defn resolve-listeners
  "Listeners in seql are handlers to run once a mutation has successfully completed.
   Each should have a unique keyword key."
  [schema kw]
  (resolve-by-entity schema kw :listeners kw))

(defn set-listener
  "Add a handler to the list of listeners for a given mutation"
  [schema mutation kw handler]
  (assoc-in schema [(resolve-entity mutation) :listeners mutation kw] handler))

(defn remove-listener
  "Remove a handler from the list of listeners for a given mutation"
  [schema mutation kw]
  (update-in schema [(resolve-entity mutation) :listeners mutation] dissoc kw))

(defn resolve-table
  "Resolve the SQL table name for a given entity specification"
  [schema kw]
  (or (resolve-by-entity schema kw :table)
      (throw (ex-info (str "unkwown table for: " kw)
                      {:type       :error/illegal-argument
                       :code       400
                       :for-entity kw}))))

(defn resolve-override
  "Look uk overrides to find a possible alternative name
   for an SQL column. When no override is found, field
   names are converted to snake case."
  [schema kw]
  (resolve-by-entity schema kw :overrides kw))

(defn resolve-field
  "Resolve a field to a fully qualified SQL identifier (containing table
   and column). Allows for column name overrides by `resolve-override`."
  [schema field]
  (let [table (resolve-table schema field)]
    (keyword
     (str (name table) "."
          (if-let [override (resolve-override schema field)]
            (name override)
            (->snake_case_string (name field)))))))

(defn resolve-fields
  "Resolves known fields from a schema"
  [schema kw]
  (or (resolve-by-entity schema kw :fields)
      (throw (ex-info (str "no field specifications for entity at: " kw)
                      {:type       :error/illegal-argument
                       :code       400
                       :for-entity kw}))))

(defn resolve-condition
  "XXX: should we keep non field conditions around, it seems a bit superfluous"
  [schema kw]
  (or (resolve-by-entity schema kw :conditions kw)
      {:type :field :field kw}))

(defn resolve-relation
  "Relations are possible joins that can be performed and later can result
   in reconstructing trees."
  [schema kw]
  (or (resolve-by-entity schema kw :relations kw)
      (throw (ex-info (str "no such relation specification: " kw)
                      {:type     :error/illegal-argument
                       :code     400
                       :relation kw}))))

(defn unresolve-table
  "Figure out the entity corresponding to a table name in SQL"
  [schema table-name]
  (first (for [[k {:keys [table]}] schema
               :when (= (name table) (str/lower-case (name table-name)))]
           (name k))))

(defn unresolve-column
  "Figure out a field name based on a table and column name in SQL."
  [schema table-name field]
  (let [entity (unresolve-table schema table-name)]
    (or (first (for [[k v] (resolve-by-entity schema (keyword entity) :overrides)
                     :when (= (str/lower-case field) (str/lower-case (name v)))]
                 k))
        (keyword entity (->kebab-case-string field)))))

;; I wonder if this belongs here, maybe in coerce?
(defn as-column-name
  [schema entity k]
  (or (get-in schema [entity :overrides k])
      (->snake_case_keyword k)))

(defn as-row
  [schema kw m]
  (let [entity    (resolve-entity kw)
        eligible? #(= (namespace %) (name entity))]
    (reduce-kv #(cond-> %1 (eligible? %2) (assoc (as-column-name schema entity %2) %3))
               {}
               m)))

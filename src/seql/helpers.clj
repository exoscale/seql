(ns seql.helpers
  "A collection of functions and macros used to help building
   SEQL schemas conforming to the spec provided in :seql.core/schema"
  (:require [clojure.edn :as edn]))

(defn- qualify
  [entity-name id]
  (keyword (format "%s/%s" (name entity-name) (name id))))

(defn- ensure-context!
  [expected forms]
  (doseq [{:keys [type context] :as form} (flatten forms)]
    (when (or (nil? type) (nil? context))
      (throw
       (ex-info "Unknown form encountered"
                {:type ::unknown-form
                 :form form})))
    (when-not (= context expected)
      (throw
       (ex-info (format "The %s form should be used inside %s definitions"
                        (if type (name type) "make-schema")
                        (if context (name context) "top-level"))
                {})))))

(defn ident
  "Marks the current field as ident, used inside field definitions"
  []
  {:type :ident :context ::field})

(def transforms
  "Commonly needed transforms for `transform`"
  {:keyword [keyword name]
   :edn     [edn/read-string pr-str]})

(defn transform
  "Provide transformation for the field. Transformations
   can either be a keyword, pointing to a known transformation
   (see `transforms` for details), or a tuple of
   `[deserializer serializer]`"
  ([short-name]
   {:type    :transform
    :context ::field
    :val     (or (get transforms short-name)
                 (throw (ex-info "bad transform shortcut" {})))})
  ([out in]
   {:type    :transform
    :context ::field
    :val     [out in]}))

(defn field
  "Define an entity field, with optional details.
   Possible detail functions include: `transform`,
   `ident`"
  [id & details]
  (ensure-context! ::field details)
  (conj (mapv #(assoc % :field id :context ::entity) details)
        {:field id :type :field :context ::entity}))

(defmacro compound
  "Create a compound field, which sources one
   or more fields from the base entity and
   yields a new computed value."
  [field source & body]
  [{:type    :compound
    :context ::entity
    :field   field
    :source  (mapv keyword source)
    :handler `(fn ~source ~@body)}])

(defn has-many
  "Express a one to many relation between the current
   entity and a remote one, expects a tuple of local
   ID to (qualified) remote ID."
  [field [local remote]]
  [{:type          :one-to-many
    :context       ::entity
    :field         field
    :remote-entity (keyword (namespace remote))
    :remote-id     remote
    :local-id      local}])

(defn has-one
  "Express a one to one relation between the current
   entity and a remote one, expects a tuple of local
   ID to (qualified) remote ID."
  [field [local remote]]
  [{:type          :one-to-one
    :context       ::entity
    :field         field
    :remote-entity (keyword (namespace remote))
    :remote-id     remote
    :local-id      local}])

(defn has-many-through
  ""
  [field [local left right remote]]
  [{:type               :many-to-many
    :context            ::entity
    :field              field
    :intermediate       (keyword (namespace left))
    :intermediate-left  left
    :intermediate-right right
    :remote-entity      (keyword (namespace remote))
    :remote-id          remote
    :local-id           local}])

(defn condition
  "Build a condition which can be used to filter results
   at the database layer.

   With a single arg, builds a condition bearing the name
   of a field to test pure equality.

   With two args, builds a condition testing equality
   against the provided field name.

   With three args, tests a field name against a provided value."
  ([field from-field value]
   {:type      :static-condition
    :context   ::entity
    :field      field
    :from-field from-field
    :value      value})
  ([field from-field]
   {:type       :field-condition
    :context   ::entity
    :field      field
    :from-field from-field})
  ([field]
   {:type       :field-condition
    :context   ::entity
    :field      field
    :from-field field}))

(defmacro inline-condition
  "Provide a function tail which shall yield a
   honeysql fragment to express a where condition on
   a field."
  [field args & body]
  {:type    :inline-condition
   :context ::entity
   :field   field
   :arity   (count args)
   :handler `(fn ~args ~@body)})

(defmacro mutation
  "Provide a function tail to perform a mutation against an entity"
  [mutation-name spec bindv & body]
  (when-not (and (vector? bindv)
                 (= 1 (count bindv)))
    (throw (ex-info "bad binding vector for mutation" {:bindv bindv})))
  {:type    :mutation
   :context ::entity
   :field   mutation-name
   :spec    spec
   :handler `(fn ~bindv ~@body)})

(defn mutation-fn
  "Provide a function to perform a named mutation"
  [mutation-name spec handler]
  {:type    :mutation
   :context ::entity
   :field   mutation-name
   :spec    spec
   :handler handler})

(defmulti ^{:no-doc true}
  merge-entity-component
  (fn [_ v] (:type v)))

(defmethod merge-entity-component :field
  [{:keys [entity] :as schema} {:keys [field]}]
  (update schema :fields conj (qualify entity field)))

(defmethod merge-entity-component :ident
  [{:keys [entity] :as schema} {:keys [field]}]
  (update schema :idents conj (qualify entity field)))

(defmethod merge-entity-component :transform
  [{:keys [entity] :as schema} {:keys [field val]}]
  (update schema :transforms assoc (qualify entity field) val))

(defmethod merge-entity-component :compound
  [{:keys [entity] :as schema} {:keys [field source handler]}]
  (assoc-in schema [:compounds (qualify entity field)]
            {:source  (mapv (partial qualify entity) source)
             :handler handler}))

(defmethod merge-entity-component :one-to-many
  [{:keys [entity] :as schema} {:keys [field] :as rel}]
  (update schema :relations
          assoc
          (qualify entity field)
          (-> rel
              (update :local-id (partial qualify entity))
              (dissoc :field :context))))

(defmethod merge-entity-component :one-to-one
  [{:keys [entity] :as schema} {:keys [field] :as rel}]
  (update schema :relations
          assoc
          (qualify entity field)
          (-> rel
              (update :local-id (partial qualify entity))
              (dissoc :field :context))))

(defmethod merge-entity-component :many-to-many
  [{:keys [entity] :as schema} {:keys [field] :as rel}]
  (update schema :relations
          assoc
          (qualify entity field)
          (-> rel
              (update :local-id (partial qualify entity))
              (dissoc :field :context))))

(defmethod merge-entity-component :static-condition
  [{:keys [entity] :as schema} {:keys [field from-field value]}]
  (update schema :conditions assoc
          (qualify entity field)
          {:type :static
           :field (qualify entity from-field)
           :value value}))

(defmethod merge-entity-component :field-condition
  [{:keys [entity] :as schema} {:keys [field from-field]}]
  (update schema :conditions assoc
          (qualify entity field)
          {:type :field
           :field (qualify entity from-field)}))

(defmethod merge-entity-component :inline-condition
  [{:keys [entity] :as schema} {:keys [field arity handler]}]
  (update schema :conditions assoc
          (qualify entity field)
          {:type    :inline
           :arity   arity
           :hanlder handler}))

(defmethod merge-entity-component :mutation
  [{:keys [entity] :as schema} {:keys [field spec handler]}]
  (update schema :mutations assoc
          (qualify entity field)
          {:spec spec :handler handler}))

(defn entity
  "Provide an entity description. Expects either a keyword or a tuple
   of `[entity-name table-name]` and a list of details as expressed by
   `field`, `compound`, `has-many`, `condition`, and `mutation`"
  [arg & components]
  (let [components (flatten components)]
    (ensure-context! ::entity components)
    (let [[entity-name table-name] (if (sequential? arg) arg [arg])]
      (reduce merge-entity-component
              {:entity     entity-name
               :table      (or table-name entity-name)
               :context    ::schema
               :type       :entity
               :fields     []
               :idents     []}
              components))))

(defn make-schema
  "Provide a complete schema of entites"
  [& entities]
  (ensure-context! ::schema entities)
  (into {} (map (juxt :entity #(dissoc % :entity :context :type)) entities)))

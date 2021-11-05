(ns seql.helpers
  "A collection of functions and macros used to help building
   SEQL schemas conforming to the spec provided in :seql.core/schema"
  (:require [clojure.spec.alpha    :as s]
            [exoscale.coax.inspect :as si]
            [seql.schema           :as schema]
            [seql.mutation         :as m]))

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

(defn ^:deprecated ident
  "Marks the current field as ident, used inside field definitions"
  []
  {:type :ident :context ::field})

(defn column-name
  "Overrides the column name for a column, will bypass name transforms through
   `camel-snake-kebab`"
  [field colname]
  {:type :column-name :context ::entity :field field :colname colname})

(defn field
  "Define an entity field"
  [id & details]
  (ensure-context! ::field details)
  (conj (mapv #(assoc % :field id :context ::entity) details)
        {:field id :type :field :context ::entity}))

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
  "Express a many to many relation between the current entity and
   a remote one through an intermediate table. Expects a tuple
   of local ID, reference to that ID in the intermediate table,
   second join field in the intermediate table, and qualified remote
   ID."
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

(defmacro ^:deprecated mutation
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

(defn add-create-mutation
  "Add a mutation to insert a new record in the database.
   By default the mutation will be named `create` in the
   corresponding's entity namespace.

   The mutation needs a spec of its input."
  ([]
   (add-create-mutation :create))
  ([create-kw]
   {:type    :create-mutation
    :context ::entity
    :spec    create-kw}))

(defn add-update-by-id-mutation
  "Add an update mutation to modify a record in the database.
   Needs an identifier field `ident`, which will be used to
   specify the target.

   By default the mutation will be named `update` in the
   corresponding's entity namespace. The ident specified
   should be provided in the input map parameters.

   The mutation needs a spec of its input."
  ([ident]
   (add-update-by-id-mutation ident :update))
  ([ident update-kw]
   {:type    :update-by-id-mutation
    :ident   ident
    :context ::entity
    :spec    update-kw}))

(defn add-delete-by-id-mutation
  "Add a delete mutation to delete a record by ID in the database.
   Needs an identifier field `ident`, which will be used to specify
   the mutation target.

   By default the mutation will be named `delete` in the
   corresponding's entity namespace. The ident specified
   should be provided in the input map parameters.

   The mutation needs a spec of its input."
  ([ident]
   (add-delete-by-id-mutation ident :delete))
  ([ident delete-kw]
   {:type    :delete-by-id-mutation
    :ident   ident
    :context ::entity
    :spec    delete-kw}))

(defn mutation-fn
  "Provide a function to perform a named mutation."
  ([mutation-name spec handler]
   {:type         :mutation
    :context      ::entity
    :field        mutation-name
    :spec         spec
    :handler      handler}))

(defn add-precondition
  ""
  [kw precondition-key handler]
  {:type     :add-precondition
   :context  ::entity
   :mutation kw
   :key      precondition-key
   :handler  handler})

(defmulti ^{:no-doc true}
  merge-entity-component
  "Adds details to a schema entity"
  (fn [_ v] (:type v)))

(defmethod merge-entity-component :field
  [{:keys [entity] :as schema} {:keys [field]}]
  (update schema :fields conj (qualify entity field)))

(defmethod merge-entity-component :column-name
  [{:keys [entity] :as schema} {:keys [field colname]}]
  (assoc-in schema [:overrides (qualify entity field)] colname))

(defmethod merge-entity-component :ident
  [schema _]
  schema)

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
           :handler handler}))

(defmethod merge-entity-component :mutation
  [{:keys [entity] :as schema} {:keys [field spec handler]}]
  (update schema :mutations assoc
          (qualify entity field)
          {:spec spec :handler handler}))

(defmethod merge-entity-component :create-mutation
  [{:keys [table entity] :as local-schema} {:keys [spec]}]
  (let [mname   (qualify entity spec)
        handler (fn [params]
                  {:insert-into [table]
                   :values      [(schema/as-row (m/schema params) entity params)]})]
    (update local-schema :mutations assoc
            mname
            {:spec mname :handler handler})))

(defmethod merge-entity-component :update-by-id-mutation
  [{:keys [table entity] :as local-schema} {:keys [ident spec]}]
  (let [mname   (qualify entity spec)
        handler (fn [params]
                  (let [id (get params ident)]
                    {:update table
                     :set    (schema/as-row (m/schema params) entity (dissoc params ident))
                     :where  [:= (schema/resolve-field (m/schema params) ident) id]}))]
    (update local-schema :mutations assoc
            mname
            {:spec mname :handler handler})))

(defmethod merge-entity-component :delete-by-id-mutation
  [{:keys [table entity] :as local-schema} {:keys [ident spec]}]
  (let [mname   (qualify entity spec)
        handler (fn [params]
                  (let [id (get params ident)]
                    {:delete-from table
                     :where       [:= (schema/resolve-field (m/schema params) ident) id]}))]
    (update local-schema :mutations assoc
            mname
            {:spec mname :handler handler})))

(defmethod merge-entity-component :add-precondition
  [{:keys [entity] :as local-schema} {:keys [handler key mutation]}]
  (let [mname   (qualify entity mutation)]
    (update-in local-schema [:mutations mname :pre] (comp vec conj) {:name key :query handler})))

(defn- keys-spec-fields
  "Makes use of `exoscale.coax.inspect/spec-root` to make a best effort
   attempt at figuring out the keys present in a specific map-like spec.

   Only keeps keys which share the provided spec."
  [spec-name]
  (letfn [(find-keys-flat [km]
            (filter keyword? (flatten km)))
          (find-keys [km]
            (set (concat (find-keys-flat (:req km))
                         (find-keys-flat (:opt km)))))
          (key-set [spec-name]
            (let [root (si/spec-root spec-name)]
              (when (coll? root)
                (let [[f & args :as _spec-form] root]
                  (condp = f
                    `s/keys       (find-keys (apply hash-map args))
                    `s/merge      (into #{} (comp (keep key-set) cat) args)
                    `s/multi-spec (key-set (s/form ((resolve (first args))))))))))]
    (into #{}
          (filter #(= (namespace %) (namespace spec-name)))
          (key-set spec-name))))

(defn entity-from-spec
  "Infer entity description from an `s/keys` spec. The related spec should only
   contain concrete fields and no relations."
  [arg & components]
  (ensure-context! ::entity (flatten components))
  (let [[entity-name table-name] (if (sequential? arg) arg [arg])]
    (reduce merge-entity-component
            {:entity  (keyword (cond-> entity-name (qualified-keyword? entity-name) namespace))
             :table   (or table-name (keyword (name entity-name)))
             :spec    entity-name
             :context ::schema
             :type    :entity
             :fields  (keys-spec-fields entity-name)}
            (flatten components))))

(defn entity
  "Provide an entity description. Expects either a keyword or a tuple
   of `[entity-name table-name]` and a list of details as expressed by
   `field`, `has-many`, `condition`, and `mutation`"
  [arg & components]
  (let [components (flatten components)]
    (ensure-context! ::entity components)
    (let [[entity-name table-name] (if (sequential? arg) arg [arg])]
      (reduce merge-entity-component
              {:entity     (keyword (cond-> entity-name (qualified-keyword? entity-name) namespace))
               :table      (or table-name (keyword (name entity-name)))
               :context    ::schema
               :type       :entity
               :fields     []}
              components))))

(defn make-schema
  "Provide a complete schema of entites"
  [& entities]
  (ensure-context! ::schema entities)
  (into {} (map (juxt :entity #(dissoc % :entity :context :type)) entities)))

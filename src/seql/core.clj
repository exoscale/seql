(ns seql.core
  "A way to interact with stored entities"
  (:require [clojure.java.jdbc      :as jdbc]
            [clojure.string         :as str]
            [clojure.spec.alpha     :as s]
            [honeysql.core          :as sql]
            [honeysql.helpers       :as h]
            [camel-snake-kebab.core :as csk]
            [seql.spec]))

;; SQL Query Builder
;; =================

(defn entity-schema
  "Look up entity in schema. Takes a schema and an entity. The entity
  argument can take be of two different shapes: it can be a keyword
  that will match the namespace of the entity in question or a coll of
  namespaced keyword (ident name) and arguments"
  [{:keys [schema]} entity]
  (if (keyword? entity)
    [entity (get schema entity)]
    (let [[ident-name & args] entity
          entity-key          (keyword (namespace ident-name))]
      [entity-key (get schema entity-key) ident-name args])))

(defn transform-out
  "Takes a namespaced keyword and prepare name transforms.
  Returns a tuple of database name to aliased name."
  [k]
  (let [entity   (namespace k)
        sql-name (csk/->snake_case (name k))]
    [(keyword (str entity "." sql-name))
     (keyword (str entity "__" (name k)))]))

(defn transform-for-join
  "Takes a namespace keyword and returns an identifier in valid SQL
  format"
  [k]
  (let [entity (namespace k)
        sql-name (csk/->snake_case (name k))]
    (keyword (str entity "." sql-name))))

(defn table-field
  [table field]
  (keyword (format "%s.%s"
                   (name table)
                   (name field))))

(defn add-ident
  "Add a where clause for an ident"
  [q entity-name ident arg]
  (h/merge-where q [:= (table-field entity-name ident) arg]))

(defmulti process-join
  "Process join by relation type. Takes a base query, a map with
  `:entity` and `:table` keys and a map of options to build the
  relation"
  #(:type %3))

(defmethod process-join :one-to-many
  [q {:keys [entity table]} {:keys [local-id remote-id remote-name]}]
  (update q :left-join conj
          [table entity]
          [:=
           (transform-for-join local-id)
           (transform-for-join (or remote-name remote-id))]))

(defn process-field
  "Add necessary stanzas to realize field targeting with SQL"
  [schema {:keys [relations compounds fields entity]}]
  (let [rel-set      (set (keys relations))
        compound-set (set (keys compounds))
        field-set    (set fields)]
    (fn [q field]
      (cond
        (and (map? field) (contains? rel-set (-> (keys field) first)))
        (let [rel-key    (first (keys field))
              subfields  (first (vals field))
              rel-schema (get relations rel-key)
              subentity  (:remote-entity rel-schema)]
          (reduce (process-field schema (get schema subentity))
                  (process-join q
                                (assoc (get schema subentity)
                                       :entity subentity)
                                rel-schema)
                  subfields))

        (contains? compound-set field)
        (let [src (:source (get compounds field))]
          (-> q
              (update :select concat (mapv transform-out src))
              (update-in [::meta ::fields] conj field)))

        (contains? field-set field)
        (-> q
            (update :select conj (transform-out field))
            (update-in [::meta ::fields] conj field))

        :else
        (throw (ex-info "unknown field type"
                        {:type  :error/illegal-argument
                         :field field}))))))

(defn build-query
  "Build a base query preparing joins when needed"
  [env entity fields]
  (let [[entity-name entity-def ident args] (entity-schema env entity)]
    (cond-> (reduce (process-field (:schema env) entity-def)
                    {:from     [[(:table entity-def) entity-name]]
                     :select   []
                     :left-join []
                     ::meta    {::entities #{entity}
                                ::entity   entity
                                ::fields   []}}
                    fields)
      (some? ident)
      (add-ident entity-name ident (first args)))))

(defn prepare-field
  "Conditionally apply field transform on field id if schema defines
  any"
  [schema field value]
  (if-let [f (second (get-in schema [(keyword (namespace field)) :transforms field]))]
    (f value)
    value))

(defn add-condition
  "If conditions are provided, add them to the query"
  [schema q [condition & args]]
  (let [entity (-> condition namespace keyword)
        table  (get-in schema [entity :table])
        params (get-in schema [entity :conditions condition])
        type   (:type params)]
    (cond
      (= type :static)
      (h/merge-where q [:= (table-field table (:field params))
                        (prepare-field schema
                                       (:field params)
                                       (:value params))])

      (= type :field)
      (if-not (= 1 (count args))
        (throw (ex-info (format "bad arity for field condition: %s" condition)
                        {:type      :error/illegal-argument
                         :code      400
                         :condition condition
                         :args      args}))
        (h/merge-where q [:= (table-field table (:field params))
                          (prepare-field schema
                                         (:field params)
                                         (first args))]))

      :else
      (if-not (= (:arity params) (count args))
        (throw (ex-info (format "bad arity for condition: %s" condition)
                        {:type      :error/illegal-argument
                         :code      400
                         :condition condition
                         :args      args}))
        (h/merge-where q (apply (:handler params) args))))))

(defn sql-query
  "Build a SQL query for the pull-syntax expressed. This is an incremental
   data-based creation of a "
  [env entity fields conditions]
  (let [[_ entity-def _ _] (entity-schema env entity)
        res (reduce #(add-condition (:schema env) %1 %2)
                    (build-query env entity (or fields (:defaults entity-def)))
                    conditions)]
    [(dissoc res ::meta) (::meta res)]))


;; Result transformations follows
;; ==============================


(defn extract-ident
  "When a query is targetting an ident,
   extract the first result"
  [entity result]
  (if (vector? entity)
    (first result)
    result))

(defn qualify-key
  "Given the enforced terminology at query time,
   yield back a qualified keyword"
  [k]
  (let [[ns tail] (str/split (name k) #"__" 2)]
    (keyword (csk/->kebab-case ns)
             (csk/->kebab-case tail))))

(defn qualify-result
  "Qualify a result with the appropriate namespace"
  [m]
  (reduce-kv #(assoc %1 (qualify-key %2) %3) {} m))

(defn process-transforms-fn
  "Yield a function which processes records and applies predefined
   transforms"
  [schema type]
  (let [transforms (reduce merge {} (map #(get % :transforms) (vals schema)))
        extract    (case type :deserialize first :serialize second)]
    (fn [m]
      (into {} (for [[k v] m :let [transform (extract (get transforms k))]]
                 [k (cond-> v (and (some? v) (some? transform)) transform)])))))

(defn compound-extra-fields
  "Figure out which fields aren't needed once compounds have been
   processed on a record"
  [compounds fields]
  (let [compound-fields (-> fields
                            (filter #(contains? (set (keys compounds)) %))
                            (mapcat (:source #(get compounds %))))]
    (set
     (remove (set fields) compound-fields))))

(defn merge-compounds-fn
  "Merge compounds into record"
  [compounds]
  (fn [record k]
    (let [{:keys [source handler]} (get compounds k)
          extract                  (apply juxt source)]
      (assoc record k (apply handler (extract record))))))

(defn process-compounds-fn
  "Yield a schema-specific function to process compounds on the fly"
  [schema {::keys [fields]}]
  (let [compounds    (reduce merge {} (map :compounds (vals schema)))
        extra-fields (compound-extra-fields compounds fields)]
    (fn [m]
      (->> fields
           (filter #(contains? (set (keys compounds)) %))
           (reduce (merge-compounds-fn compounds) m)
           (filter #(not (contains? extra-fields (key %))))
           (into {})))))

(defn recompose-relations
  "The join query perfomed by `query` returns a flat list of entries,
   potentially unsorted (this is database implementation specific)
   recompose a tree of entities as specified in fields.

   "
  [fields records]
  (letfn [(add-relation-fn [group]
            (fn [record relation]
              (let [rel-key    (first (keys relation))
                    rel-fields (first (vals relation))]
                (assoc record rel-key (walk-tree rel-fields group)))))
          (walk-tree [fields records]
            (let [plain-fields (remove map? fields)
                  relations    (filter map? fields)
                  partitioner  (apply juxt plain-fields)
                  extract      #(select-keys % plain-fields)
                  groups       (->> (sort-by partitioner records)
                                    (partition-by partitioner))]
              (if (empty? relations)
                (remove #(every? nil? (vals %))
                        (map #(extract (first %)) groups))
                (for [g groups]
                  (reduce (add-relation-fn g) (extract (first g)) relations)))))]
    (walk-tree fields records)))

(defn query
  "Look up entities."
  ([env entity]
   (let [[_ entity-def _ _] (entity-schema env entity)]
     (query env entity (:fields entity-def) [])))
  ([env entity fields]
   (query env entity fields []))
  ([env entity fields conditions]
   (s/assert ::query-args [env entity fields conditions])
   (let [[q qmeta] (sql-query env entity fields conditions)]
     (->> (sql/format q)
          (jdbc/query (:jdbc env))
          (map qualify-result)
          (map (process-transforms-fn (:schema env) :deserialize))
          (map (process-compounds-fn (:schema env) qmeta))
          (recompose-relations fields)
          (extract-ident entity)))))

;; Mutation support
;; ================

(defn find-mutation
  "Fetch mutation description"
  [env mutation]
  (let [entity (-> mutation namespace keyword)]
    (or (get-in env [:schema entity :mutations mutation])
        (throw (ex-info (format "unknown mutation: %s" mutation)
                        {:type     :error/illegal-argument
                         :code     400
                         :mutation mutation})))))

(defn find-listeners
  "Fetch listeners"
  [env mutation]
  (let [entity (-> mutation namespace keyword)]
    (or (get-in env [:schema entity :listeners mutation]) {})))

(defn mutate!
  "Perform a mutation. Since mutations are spec'd, parameters are
   expected to conform it."
  ([env mutation params]
   (mutate! env mutation params {}))
  ([env mutation params metadata]
   (s/assert ::mutate-args [env mutation params])
   (let [{:keys [spec handler]} (find-mutation env mutation)
         listeners              (find-listeners env mutation)]

     (when-not (s/valid? spec params)
       (throw (ex-info (format "mutation params do not conform to %s: %s"
                               spec
                               (s/explain-str spec params))
                       {:type    :error/illegal-argument
                        :code    400
                        :explain (s/explain-str spec params)})))

     (let [result (jdbc/with-db-transaction [jdbc (:jdbc env)]
                    (let [transform (process-transforms-fn (:schema env)
                                                           :serialize)
                          statement (-> (transform params)
                                        (handler)
                                        (sql/format))]
                      (jdbc/execute! jdbc statement)))]

       (when (< (first result) 1)
         (throw (ex-info (format "the mutation has failed: %s" mutation)
                         {:type     :error/mutation-failed
                          :code     404 ;; Likely the mutation has failed
                          ;; because the where clauses did
                          ;; not match
                          :mutation mutation
                          :params   params})))
       (run! (fn [[key listener]]
               (listener {:key key
                          :mutation mutation
                          :result   result
                          :params   params
                          :metadata metadata}))
             listeners)
       result))))

;; Environment modifiers
;; =====================

(defn add-listener!
  "Given an environment, add a mutation handler.
  The handlers is bound by `key`, if specified, otherwise the `key` will
  default to the mutation key. Yields an updated environment"
  ([env mutation handler]
   (add-listener! env mutation mutation handler))
  ([env mutation key handler]
   (let [entity (-> mutation namespace keyword)]
     (update-in env
                [:schema entity :listeners mutation key]
                (fn [h]
                  (when h
                    (throw (ex-info (format "Listener already registered for %s"
                                            key)
                                    {:type ::already-registered-error
                                     :key key})))
                  handler)))))

(defn remove-listener!
  "Given an environment, remove a mutation handler by `key` if
  specified, otherwise it will remove a handler that match the
  mutation `key`. Yields an updated environment."
  ([env mutation]
   (remove-listener! env mutation mutation))
  ([env mutation key]
   (let [entity (-> mutation namespace keyword)]
     (update-in env [:schema entity :listeners mutation] dissoc key))))

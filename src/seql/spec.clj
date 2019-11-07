(ns seql.spec
  (:require [clojure.spec.alpha :as s]))

(defn qualified-entity?
  [[entity
    {:keys [idents fields transforms conditions
            compounds relations mutations listeners]}]]
  (let [qualified? (partial = (name entity))]
    (and (every? qualified? (map namespace idents))
         (every? qualified? (map namespace fields))
         (every? qualified? (map namespace (keys conditions)))
         (every? qualified? (map namespace (keys relations)))
         (every? qualified? (map namespace (keys mutations)))
         (every? qualified? (map namespace (keys compounds)))
         (every? qualified? (map namespace (keys transforms))))))

(defn valid-fields?
  [{:keys [idents fields transforms conditions compounds]}]
  (let [valid-field? (partial contains? (set fields))]
    (and (every? valid-field? idents)
         (every? valid-field? (keys transforms))
         (every? valid-field? (mapcat :source (vals compounds))))))

(defmulti valid-local-relation? :type)

(defmethod valid-local-relation? :one-to-many
  [{:keys [entity local-id]}]
  (= (name entity) (namespace local-id)))

(defmulti valid-remote-relation? :type)

(defmethod valid-remote-relation? :one-to-many
  [{:keys [remote-entity remote-id]}]
  (= (name remote-entity) (namespace remote-id)))

(defn valid-relations?
  [m]
  (every?
   true?
   (for [[entity {:keys [relations]}] m]
     (every?
      true?
      (for [[_ relation] relations]
        (and
         (valid-local-relation? (assoc relation :entity entity))
         (valid-remote-relation? relation)))))))

(defn qualified-schema?
  [m]
  (and (every? qualified-entity? m)
       (valid-relations? m)))

(defmulti condition-dispatch :type)

(defmethod condition-dispatch :static
  [_]
  (s/keys :req-un [::field]))

(defmethod condition-dispatch :field
  [_]
  (s/keys :req-un [::field]))

(defmethod condition-dispatch :inline
  [_]
  (s/keys :req-un [::arity ::handler]))

(defmulti relation-dispatch :type)

(defmethod relation-dispatch :one-to-many
  [_]
  (s/keys :req-un [::remote-entity ::local-id ::remote-id]))

(defmethod condition-dispatch :field
  [_]
  (s/keys :req-un [::field]))

(defmethod condition-dispatch :inline
  [_]
  (s/keys :req-un [::arity ::handler]))

(s/def ::field keyword?)
(s/def ::value any?)
(s/def ::arity nat-int?)
(s/def ::handler fn?)
(s/def ::spec (s/or :kw keyword? :spec s/spec?))

(create-ns 'seql.pre)
(s/def seql.pre/name string?)
(s/def seql.pre/handler fn?)
(s/def seql.pre/valid? fn?)
(s/def ::pre-condition (s/keys :req-un [:seql.pre/name :seql.pre/handler]
                               :opt-un [:seql.pre/valid?]))
(s/def ::pre (s/coll-of ::pre-condition))

(s/def ::table keyword?)
(s/def ::idents (s/coll-of keyword?))
(s/def ::fields (s/coll-of keyword?))

(s/def ::remote-entity keyword?)
(s/def ::local-id keyword?)
(s/def ::remote-id keyword?)

(s/def ::serdes (s/cat :serializer fn? :deserializer fn?))
(s/def ::transforms (s/map-of keyword? ::serdes))
(s/def ::condition (s/multi-spec condition-dispatch :type))
(s/def ::conditions (s/map-of keyword? ::condition))

(s/def ::relation (s/multi-spec relation-dispatch :type))
(s/def ::relations (s/map-of keyword? ::relation))

(s/def ::mutation (s/keys :req-un [::spec ::handler]
                          :opt-un [::pre]))
(s/def ::mutations (s/map-of keyword? ::mutation))

(s/def ::compound (s/keys :req-un [::source ::handler]))
(s/def ::compounds (s/map-of keyword? ::compound))

(s/def :seql.core/entity-def
  (s/and (s/keys :req-un [::table ::idents ::fields]
                 :opt-un [::transforms ::conditions ::relations
                          ::mutations ::compounds])))
(s/def :seql.core/schema
  (s/and (s/map-of keyword? :seql.core/entity-def)
         qualified-schema?))

(s/def :seql.core/jdbc any?)

(s/def :seql.core/env
  (s/keys :req-un [:seql.core/schema :seql.core/jdbc]))

(s/def :seql.core/condition (s/cat :name keyword? :args (s/* any?)))

(s/def :seql.core/seql-relation
  (s/and (s/map-of keyword? :seql.core/seql-query)
         #(= 1 (count %))))

(s/def :seql.core/seql-query
  (s/coll-of (s/or :field keyword?
                   :relation :seql.core/seql-relation)))

(s/def :seql.core/seql-entity
  (s/or :field keyword? :ident (s/cat :ident keyword? :arg any?)))

(s/def :seql.core/conditions (s/coll-of :seql.core/condition))

(defn valid-query-conditions?
  [{:keys [env query conditions]}]
  (let [schema           (:schema env)
        known-conditions (set (mapcat (comp keys :conditions) (vals schema)))]
    (every? #(contains? known-conditions %) (map :name conditions))))

(defn flatten-query
  [top-level-query]
  (letfn [(walker [query]
            (loop [[head & tail] query
                   relations     []
                   fields        []]
              (cond
                (nil? head)
                [(vec relations) (vec fields)]

                (keyword? head)
                (recur tail relations (conj fields head))

                (map? head)
                (let [[r subquery]             (first (seq head))
                      [subrelations subfields] (walker subquery)]
                  (recur tail
                         (concat (conj relations r) subrelations)
                         (concat fields subfields))))))]
    (walker top-level-query)))

(defn valid-query-fields?
  [{:keys [env query]}]
  (let [schema             (:schema env)
        known-fields       (set
                            (concat (mapcat :fields (vals schema))
                                    (mapcat (comp keys :compounds)
                                            (vals schema))))

        known-relations    (set (mapcat (comp keys :relations) (vals schema)))
        [relations fields] (flatten-query
                            (s/unform :seql.core/seql-query query))]
    (and
     (every? #(contains? known-fields %) fields)
     (every? #(contains? known-relations %) relations))))

(s/def :seql.core/query-args
  (s/and
   (s/cat :env :seql.core/env
          :entity :seql.core/seql-entity
          :query :seql.core/seql-query
          :conditions :seql.core/conditions)
   valid-query-conditions?
   valid-query-fields?))

(s/def :seql.core/mutate-args
  (s/cat :env :seql.core/env
         :mutation keyword?
         :params   map?))

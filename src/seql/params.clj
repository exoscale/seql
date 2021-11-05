(ns ^:no-doc seql.params
  "Functions to translate seql queries into input for
   honeysql"
  (:require [clojure.spec.alpha :as s]
            [seql.coerce        :as c]
            [seql.schema        :as schema]))

(defn- ^:no-doc find-selections
  "Walks a field definition to find plain fields,
   returns a flat list of fields"
  [schema q]
  (letfn [(list-extract-fields [l]
            (into [] cat (map extract-fields l)))
          (extract-fields [[k v]]
            (case k
              :field    [(schema/resolve-field schema v)]
              :relation (list-extract-fields (first (vals v)))))]
    (list-extract-fields q)))

(defn- ^:no-doc find-joins
  "Walks a field definition to find plain fields,
   returns a flat list of fields"
  [q]
  (letfn [(list-extract-joins [l]
            (into [] cat (map extract-joins l)))
          (extract-joins [[k v]]
            (case k
              :field    []
              :relation (conj (list-extract-joins (first (vals v)))
                              (first (keys v)))))]
    (-> (list-extract-joins q)
        (reverse)
        (vec))))

(defn expand-condition
  [schema [condition & args]]
  (let [params               (schema/resolve-condition schema condition)
        {:keys [type field]} params
        table-field          (when (some? field) (schema/resolve-field schema field))]
    (cond
      (= type :static)
      [:= table-field (c/write field (:value params))]

      (= type :field)
      (case (count args)
        0 (throw (ex-info (format "bad arity for field condition: %s" condition)
                          {:type      :error/illegal-argument
                           :code      400
                           :condition condition
                           :args      args}))
        1 [:= table-field (c/write field (first args))]
        [:in table-field (map #(c/write field %) args)])

      :else
      (if-not (= (:arity params) (count args))
        (throw (ex-info (format "bad arity for condition: %s" condition)
                        {:type      :error/illegal-argument
                         :code      400
                         :condition condition
                         :args      args}))
        (apply (:handler params) args)))))

(defn resolve-ident-condition
  [schema e]
  (when (coll? e)
    (let [[field val] e]
      [:= (schema/resolve-field schema field) (c/write field val)])))

(s/def :seql.query/seql-relation (s/and (s/map-of qualified-keyword? :seql.query/seql-query)
                                        #(= 1 (count %))))
(s/def :seql.query/seql-query    (s/coll-of (s/or :field qualified-keyword?
                                                  :relation :seql.query/seql-relation)))

(defn for-query
  [schema entity fields conditions]
  (let [fields          (if (seq fields) fields (schema/resolve-fields schema entity))
        cfields         (s/conform :seql.query/seql-query fields)
        selections      (find-selections schema cfields)
        ident-condition (resolve-ident-condition schema entity)
        joins           (find-joins cfields)]
    {:table      (schema/resolve-table schema entity)
     :fields     fields
     :ident?     (some? ident-condition)
     :selections selections
     :conditions (seq (cond-> (map (partial expand-condition schema) conditions)
                        (some? ident-condition)
                        (conj ident-condition)))
     :joins      joins}))

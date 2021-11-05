(ns seql.query
  "Query facilities against SEQL environments"
  (:require [next.jdbc              :as jdbc]
            [clojure.spec.alpha     :as s]
            [honey.sql              :as sql]
            [seql.env               :as env]
            [seql.tree              :as tree]
            [seql.schema            :as schema]
            [seql.coerce            :as c]
            [seql.params            :as params]
            [seql.spec              :as spec]
            [seql.relation          :as relation]
            [seql.result-set        :as result-set]))

(defn ^:no-doc into-query
  "Transform the output of `seql.params/for-query` into a valid
   honeysql query."
  [schema {:keys [table selections conditions joins]}]
  (cond-> {:select    selections
           :from      table}
    (seq conditions)
    (assoc :where (if (= 1 (count conditions))
                    (first conditions)
                    (concat [:and] conditions)))
    (seq joins)
    (assoc :left-join
           (mapcat (partial relation/resolve-and-expand schema) joins))))

(defn ^:no-doc sql-query
  "Return an SQL query ready for `next.jdbc/execute` based on the
  output of `seql.params/for-query`."
  [schema params]
  (->> (into-query schema params)
       (sql/format)))

(defn educt
  "Run a SEQL query and return an eduction of the records. No tree recomposition
   is performed, joined rows will be returned one by one. Since SEQL's tree recomposition
   sorts and groups records it must hold all of the returned result set in memory. When querying
   large datasets, `educt` can be used to limit memory usage and ensure faster consumption."
  ([env entity fields conditions]
   (let [params (params/for-query (env/schema env) entity fields conditions)]
     (educt env params)))
  ([{:keys [schema jdbc]} params]
   (let [opts {:builder-fn (result-set/builder-fn schema)}
         q    (sql-query schema params)]
     (eduction (map c/read-map) (jdbc/execute! jdbc q opts)))))

(defn execute
  "Execute an EQL query. Accepts an environment and the target entity or an ident specification.
   Optionally takes the list of fields (including relation fields) to resolve and conditions."
  ([env entity]
   (execute env entity (schema/resolve-fields (env/schema env) entity) []))
  ([env entity fields]
   (execute env entity fields []))
  ([env entity fields conditions]
   (let [schema                            (env/schema env)
         {:keys [joins ident?] :as params} (params/for-query schema entity fields conditions)
         results                           (educt env params)]
     (cond
       (and (seq joins) ident?)
       (first (tree/build schema params results))

       ident?
       (first results)

       (seq joins)
       (tree/build schema params results)

       :else
       (into [] results)))))

(s/def ::condition     (s/cat :name qualified-keyword? :args (s/* any?)))
(s/def ::conditions    (s/coll-of ::condition))
(s/def ::seql-entity   (s/or :field keyword?
                             :ident (s/cat :ident keyword? :arg any?)))
(s/def ::query-args    (s/and
                        (s/cat :env ::spec/env
                               :entity ::seql-entity
                               :query ::seql-query
                               :conditions ::conditions)
                        spec/valid-query-conditions?
                        spec/valid-query-fields?))

(ns seql.mutation
  "A way to interact with stored entities"
  (:require [next.jdbc          :as jdbc]
            [next.jdbc.transaction :as tx]
            [clojure.spec.alpha :as s]
            [honey.sql          :as sql]
            [seql.coerce        :as c]
            [seql.schema        :as schema]
            [seql.listener      :as listener]
            [seql.env           :as env]
            [seql.spec]))

(defn- success-result?
  "Asserts success back from JDBC, should be tested on
   more database implementations."
  [result]
  (some-> result first :next.jdbc/update-count pos?))

(defn- run-preconditions!
  "Preconditions are plain queries to run before a mutation to assert that the
   database is in the expected state.

   XXX: should allow for different forms, including a function of the output
   of an EQL query."
  [jdbc mutation params pre]
  (when (seq pre)
    (run! (fn [{:keys [name query valid?]
                :or   {valid? seq}
                :as   pre}]
            (when-let [q (query params)]
              (let [result (jdbc/execute! jdbc (sql/format q))]
                (when-not (valid? result)
                  (throw (ex-info (format "Precondition %s on mutation %s failed"
                                          name
                                          mutation)
                                  {:type     :error/mutation-failed
                                   :code     409
                                   :mutation mutation
                                   :params   (dissoc params ::metadata ::schema)
                                   :pre      (dissoc pre :valid? :query)}))))))
          pre)))

(defn- wrap-statements
  "Transform returned statement value into a vector, allowing handlers to return
   multiple statements"
  [x]
  (if (vector? x) x [x]))

(defn mutate!
  "Perform a mutation. Since mutations are spec'd, parameters are
   expected to conform it."
  ([env mutation params]
   (mutate! env mutation params {}))
  ([env mutation params metadata]
   (s/assert ::mutate-args [env mutation params])
   (let [{:keys [spec handler pre]} (schema/resolve-mutation (env/schema env) mutation)]
     (when-not (s/valid? spec params)
       (throw (ex-info (format "mutation params do not conform to %s: %s"
                               spec
                               (s/explain-str spec params))
                       {:type    :error/illegal-argument
                        :code    400
                        :explain (s/explain-str spec params)})))
     (let [cparams     (c/write-map params)
           statements  (map sql/format (-> cparams
                                           (assoc ::schema (env/schema env))
                                           (assoc ::metadata metadata)
                                           handler
                                           wrap-statements))
           result      (jdbc/with-transaction [tx (env/jdbc env)]
                         ;; if we have preconditions check these first
                         (run-preconditions! tx mutation (dissoc cparams ::schema ::metadata) pre)
                         (last (map #(jdbc/execute! tx %) statements)))]
       (when-not (success-result? result)
         (throw (ex-info (format "the mutation has failed: %s" mutation)
                         {:type     :error/mutation-failed
                          :code     404 ;; Likely the mutation has failed
                          ;; because the where clauses did not match
                          :mutation mutation
                          :params   params})))
       (listener/notify! (env/schema env) mutation
                         {:result   result
                          :params   params
                          :metadata metadata})
       result))))

(def schema
  "Convenience function to access the SEQL schema. To be used in mutation functions"
  ::schema)

(def metadata
  "Convenience function to access metadata provided to a mutation. To be used in mutation functions"
  ::metadata)

(defn run-in-transaction
  "Run a function of a single argument (an environment set-up to run
   in a transaction), allowing to perform isolated queries and mutations."
  [env f]
  (jdbc/with-transaction [tx (env/jdbc env)]
    (binding [tx/*nested-tx* :ignore]
      (let [env (assoc env :jdbc tx)]
        (f env)))))

(defmacro with-transaction
  "Run the forms in `fntail` in the context of a transaction against the
   provided `env` environment."
  [sym & fntail]
  `(run-in-transaction ~sym (fn [~sym] ~@fntail)))

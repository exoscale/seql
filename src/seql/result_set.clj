(ns ^:no-doc seql.result-set
  "Utility functions to transform SQL rows returned
   by JDBC into well qualified records."
  (:require [next.jdbc.result-set :as rs]
            [seql.schema          :as schema])
  (:import java.sql.ResultSet
           java.sql.ResultSetMetaData))

(defn- get-column-names
  "Reverse lookup of column name for rows."
  [schema ^ResultSetMetaData rsmeta]
  (mapv (fn [^Integer i]
          (schema/unresolve-column schema
                                   (.getTableName rsmeta i)
                                   (.getColumnLabel rsmeta i)))
        (range 1 (inc (if rsmeta (.getColumnCount rsmeta) 0)))))

(defn builder-fn
  "A `builder-fn` for `next.jdbc/execute`. This function is kept out of
   the built documentation to not confuse readers, but could be used directly
   with `next.jdbc/execute` outside of SEQL queries if necessary to get qualified
   entities out of the database."
  [schema]
  (fn [^ResultSet rs _]
    (let [rsmeta (.getMetaData rs)]
      (rs/->MapResultSetBuilder rs rsmeta (get-column-names schema rsmeta)))))

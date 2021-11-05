(ns ^:no-doc seql.relation
  "Handlers for entity relations."
  (:refer-clojure :exclude [key type])
  (:require [seql.schema :as schema]))

(defmulti expand
  "Expand a relation definition to a honeysql left-join operation based on its specification.
   Yields a vector with table names and selection interleaved."
  #(:type %2))

(defmethod expand :one-to-many
  [schema {:keys [local-id remote-id]}]
  [(schema/resolve-table schema remote-id)
   [:= (schema/resolve-field schema local-id) (schema/resolve-field schema remote-id)]])

(defmethod expand :one-to-one
  [schema {:keys [local-id remote-id]}]
  [(schema/resolve-table schema remote-id)
   [:= (schema/resolve-field schema local-id) (schema/resolve-field schema remote-id)]])

(defmethod expand :many-to-many
  [schema {:keys [local-id
                  intermediate-left
                  intermediate-right
                  remote-id]}]
  [(schema/resolve-table schema intermediate-left)
   [:= (schema/resolve-field schema local-id)
    (schema/resolve-field schema intermediate-left)]
   (schema/resolve-table schema remote-id)
   [:= (schema/resolve-field schema intermediate-right)
    (schema/resolve-field schema remote-id)]])

(defn resolve-and-expand
  [schema kw]
  (expand schema (schema/resolve-relation schema kw)))

(defmulti finalize
  "Perform any necessary final steps once relations field have been
   fetched."
  (fn [{:keys [type]} _] type))

(defmethod finalize :one-to-one
  [_ results]
  (first results))

(defmethod finalize :default
  [_ results]
  results)

(defn key
  "A relation specification always takes the form of a single-entry map of
   relation name to fields in the remote entity. This yields the relation name."
  [rel]
  (first (keys rel)))

(defn fields
  "A relation specification always takes the form of a single-entry map of
   relation name to fields in the remote entity. This yields the fields specification."
  [rel]
  (first (vals rel)))

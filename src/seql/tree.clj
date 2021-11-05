(ns ^:no-doc seql.tree
  "Facilities to build trees of records based on a supplied
   EQL field expression. This is exists because even though
   LEFT JOIN queries build ad-hoc trees of data, a flat
   (potentially unsorted!) list of records is what SQL will
   give. The `build` function builds back the tree."
  (:require [seql.schema   :as schema])
  (:require [seql.relation :as relation]))

(defn- create-groups
  "Build a sort function for relations. Maps values are replaced by nil
  in order to not use them in the comparison.

  It could be tempting to reach for `select-keys` here but it would forfeit
  the option to disregard non-scalar types which are necessary for correct
  grouping.

  XXX: a further refinement would be to have a way to enforce that foreign
  keys make it to the list of selections and are later removed from the output,
  allowing use foreign keys as the grouping predicate."
  [fields records]
  (let [get-fields  (apply juxt fields)
        partitioner (fn [record] (into [] (remove map?) (get-fields record)))]
    (->> records
         (sort-by partitioner)
         (partition-by partitioner))))

(defn- empty-row?
  "Predicate to test for empty rows"
  [row]
  (every? nil? (vals row)))

(defn build
  "The join query perfomed by `seql.query/execute` returns a flat list of entries,
   potentially unsorted (this is database implementation specific), and
   recompose a tree of entities as specified in fields.

   For instance let's assume the following list of records coming from a database
   query:

       [{:account/id 0 :user/id 0}
        {:account/id 0 :user/id 1}
        {:account/id 2 :user/id 3}]

   Calling `build` with a field definition of `[:account/id {:account/users [:user/id]}]`
   will group the records together into:

       [{:account/id 0 :account/users [{:user/id 0} {:user/id 1}]}
        {:account/id 2 :account/users [{:user/id 3}]}]

   Under the hood, the implementation keeps recursing between two functions:

   - `walk-tree` which groups records together
   - `add-relation-fn` which builds a closure to add nested fields belonging to a relation
"
  [schema {:keys [fields]} records]
  ;; We keep recursing between two functions:
  ;; - `walk-tree` which groups records together
  ;; - `add-relation-fn` which builds a closure to add further
  ;;   nest fields belonging to relations
  (letfn [(add-relation-fn [group]
            (fn [record rel-def]
              (let [rel-key  (relation/key rel-def)
                    relation (schema/resolve-relation schema rel-key)
                    results  (into [] (remove empty-row?) (walk-tree (relation/fields rel-def) group))]
                (cond-> record
                  (seq results) (assoc rel-key (relation/finalize relation results))))))
          (walk-tree [fields records]
            (let [plain-fields (remove map? fields)
                  relations    (filter map? fields)
                  groups       (create-groups plain-fields records)]
              (for [group groups
                    :let  [row (select-keys (first group) plain-fields)]]
                (if (empty? relations)
                  row
                  (reduce (add-relation-fn group) row relations)))))]
    (walk-tree fields records)))

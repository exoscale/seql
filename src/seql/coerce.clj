(ns seql.coerce
  "Handling of field coercion for read/write"
  (:refer-clojure :exclude [read])
  (:require [clojure.edn :as edn]
            [exoscale.coax :as c]))

(def ^:no-doc spec-registry
  (atom {::reader {}
         ::writer {:idents {`keyword? (fn [x _] (name x))}}}))

(defn edn-reader
  "An example reader for arbitrary values stored in EDN in the database"
  [x _]
  (edn/read-string x))

(defn edn-writer
  "An example writer for arbitrary values stored in EDN in the database"
  [x _]
  (pr-str x))

(defn ^:no-doc -register-rw!
  [type spec-key coercer]
  (swap! spec-registry assoc-in [type :idents spec-key] coercer)
  spec-key)

(defn with-writer!
  "Registers a writer for `spec`, will be called for serialisation before
   entering the database."
  [spec-key w]
  (-register-rw! ::writer spec-key w))

(defn with-reader!
  "Registers a reader for `spec`, will be called for deserialisation on
   database fed values."
  [spec-key w]
  (-register-rw! ::reader spec-key w))

(defn ^:no-doc write
  "Infer from field spec if we need to use ::overrides or just a
  predicate registered transform. Suports Set spec types inference and
  nilables"
  [k v]
  (c/coerce k v (::writer @spec-registry)))

(defn ^:no-doc read
  "Infer from specs+ reader registry now to read value of attribute `k`"
  [k v]
  (c/coerce k v (::reader @spec-registry)))

(defn ^:no-doc read-map
  "Read all fields of a map with `read`. Keys need to have a corresponding
   spec and optional reader attached for any coercion to be performed."
  [m]
  (reduce-kv #(cond-> %1 (some? %3) (assoc %2 (read %2 %3))) {} m))

(defn ^:no-doc write-map
  "Transform all fields of a map with `write`. Keys need to have a corresponding
   spec and optional reader attached for any coercion to be performed."
  [m]
  (reduce-kv #(assoc %1 %2 (write %2 %3)) {} m))

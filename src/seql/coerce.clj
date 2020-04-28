(ns seql.coerce
  "Handling of field coercion for read/write"
  (:refer-clojure :exclude [read])
  (:require [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [exoscale.coax :as sc]))

(def spec-registry (atom {::reader {}
                          ::writer {}}))

(defn edn-reader [x _] (edn/read-string x))
(defn edn-writer [x _] (pr-str x))

(defn -register-rw!
  [type spec-key coercer]
  (swap! spec-registry
         assoc-in [type spec-key] coercer))

(defn with-writer!
  "Registers a writer for `spec`"
  [spec-key w]
  (-register-rw! ::writer spec-key w)
  spec-key)

(defn with-reader!
  "Registers a reader for `spec`"
  [spec-key w]
  (-register-rw! ::reader spec-key w)
  spec-key)

(def pred-writer-registry
  (atom {`keyword? (fn [x _] (name x))}))

(defn write
  "Infer from field spec if we need to use ::overrides or just a
  predicate registered transform. Suports Set spec types inference and
  nilables"
  [k v]
  (let [rs (some-> k
                   s/get-spec
                   s/form) ; get original spec form
        s (sc/pull-nilable rs) ; in case it's nilable pull original form
        f (get @pred-writer-registry ; find writer for original form
               (cond-> s
                 (sc/spec-is-homogeneous-set? s)
                 (-> first sc/type->sym)))]
    (if (ifn? f) ; we got a custom writer
      (if (sc/nilable-spec? rs)
        ((sc/gen-nilable-coercer f) v nil) ; if nilable we must make the coercer aware
        (f v nil))
      (sc/coerce k v {::sc/overrides (::writer @spec-registry)}))))

(defn read
  "Infer from specs+ reader registry now to read value of attribute `k`"
  [k v]
  (sc/coerce k v {::sc/overrides (::reader @spec-registry)}))

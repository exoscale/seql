(ns seql.coerce
  "Handling of field coercion for read/write"
  (:refer-clojure :exclude [read])
  (:require [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [exoscale.coax :as c]
            [exoscale.coax :as sc]))

(defprotocol Coercion
  (get-name [this])
  (get-coercer [this spec])
  (get-validater [this spec])
  (get-explainer [this spec]))

(def default-coercion
  (reify Coercion
    (get-name [_] :spec)
    (get-coercer [_ spec]
      (fn [value]
        (sc/coerce spec value)))
    (get-validater [_ spec]
      (fn [value]
        (s/valid? spec value)))
    (get-explainer [_ spec]
      (fn [value]
        (s/explain-str spec value)))))

(def spec-registry (atom {::reader {}
                          ::writer {::c/idents {`keyword? (fn [x _] (name x))}}}))

(defn edn-reader [x _] (edn/read-string x))
(defn edn-writer [x _] (pr-str x))

(defn -register-rw!
  [type spec-key coercer]
  (swap! spec-registry
         assoc-in [type ::c/idents spec-key] coercer))

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

(defn write
  "Infer from field spec if we need to use ::overrides or just a
  predicate registered transform. Suports Set spec types inference and
  nilables"
  [k v]
  (c/coerce k v (::writer @spec-registry)))

(defn read
  "Infer from specs+ reader registry now to read value of attribute `k`"
  [k v]
  (c/coerce k v (::reader @spec-registry)))

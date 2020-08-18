(ns seql.string
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.internals.string-separator :as csk-sep]
            [clojure.string :as str]))

(defn case-split
  "Adapted from clj-commons/camel-snake-kebab to ignore numbers as
  separators for kebab/camel casing."
  [ss]
  (let [cs (mapv csk-sep/classify-char ss)
        ss-length (.length ^String ss)]
    (loop [result (transient []), start 0, current 0]
      (let [next (inc current)
            result+new (fn [end]
                         (if (> end start)
                           (conj! result (.substring ^String ss start end))
                           result))]
        (cond (>= current ss-length)
              (or (seq (persistent! (result+new current)))
                  [""])

              (= (nth cs current) :whitespace)
              (recur (result+new current) next next)

              (let [[a b c] (subvec cs current)]
                (or (and (not= a :upper)  (= b :upper))
                    (and (= a :upper) (= b :upper) (= c :lower))))
              (recur (result+new next) next next)

              :else
              (recur result start next))))))

(def separator
  (reify csk-sep/StringSeparator
    (split [_ s] (case-split s))))

(defn ->snake
  [s]
  (csk/convert-case str/lower-case
                    str/lower-case
                    "_"
                    s
                    :separator separator))

(defn ->kebab
  [s]
  (csk/convert-case str/lower-case
                    str/lower-case
                    "-"
                    s
                    :separator separator))

(ns daiquiri.util
  (:require [clojure.set :refer [rename-keys]]
            [clojure.string :as str]))

(defn valid-key? [k]
  (or (keyword? k)
      (string? k)
      (symbol? k)))

(defn -camel-case [k]
  (if (string? k)
    k
    (let [[first-word & words] (.split (name k) "-")]
      (if (or (empty? words)
              (= "aria" first-word)
              (= "data" first-word))
        k
        (-> (map str/capitalize words)
            (conj first-word)
            str/join
            keyword)))))

(def attrs-cache (volatile! {}))

(defn camel-case
  "Returns camel case version of the key, e.g. :http-equiv becomes :httpEquiv.
  Does not convert string attributes."
  [k]
  (if (valid-key? k)
    (or (get @attrs-cache k)
        (let [kk (-camel-case k)]
          (vswap! attrs-cache assoc k kk)
          kk))
    k))

(defn camel-case-keys* [m]
  (->> (reduce-kv #(assoc! %1 (camel-case %2) %3)
                  (transient {})
                  m)
       persistent!))

(defn camel-case-keys
  "Recursively transforms all map keys into camel case."
  [m]
  (if (map? m)
    (let [m (->> m
                 (reduce-kv #(assoc! %1 (camel-case %2) %3)
                            (transient {}))
                 persistent!)]
      (cond-> m
        (map? (:style m))
        (update :style camel-case-keys)))
    m))

(defn fragment-tag?
  "Returns true if `tag` is the fragment tag \"*\" or \"<>\", otherwise false."
  [tag]
  (and (or (keyword? tag)
           (symbol? tag)
           (string? tag))
       (or (= (name tag) "*")
           (= (name tag) "<>"))))

(defn fragment? [v]
  (and (vector? v)
       (fragment-tag? (nth v 0 nil))))

(defn element?
  "Return true if `x` is an HTML element. True when `x` is a vector
  and the first element is a keyword, e.g. `[:div]` or `[:div [:span \"x\"]`."
  [x]
  (and (vector? x)
       (keyword? (nth x 0 nil))))

(defn html-to-dom-attrs
  "Converts all HTML attributes to their DOM equivalents."
  [attrs]
  (rename-keys (camel-case-keys attrs)
               {:class :className
                :for :htmlFor}))

(defn join-classes
  "Join the `classes` with a whitespace."
  [classes]
  (->> classes
       (into [] (comp
                 (mapcat (fn [x] (if (string? x) [x] (seq x))))
                 (remove nil?)))
       (str/join " ")))

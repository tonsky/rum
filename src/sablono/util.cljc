(ns sablono.util
  (:require [clojure.set :refer [rename-keys]]
            [clojure.string :as str]))

(defn -camel-case [k]
  (if (or (keyword? k)
          (string? k)
          (symbol? k))
    (let [[first-word & words] (.split (name k) "-")]
      (if (or (empty? words)
              (= "aria" first-word)
              (= "data" first-word))
        k
        (-> (map str/capitalize words)
            (conj first-word)
            str/join
            keyword)))
    k))

#?(:cljs (def attrs-cache #js {:class "className"
                               :for "htmlFor"}))

(defn camel-case
  "Returns camel case version of the key, e.g. :http-equiv becomes :httpEquiv."
  [k]
  #?(:clj (-camel-case k)
     :cljs (or (aget attrs-cache (name k))
               (let [cck (name (-camel-case k))]
                 (aset attrs-cache (name k) cck)
                 cck))))

(defn camel-case-keys
  "Recursively transforms all map keys into camel case."
  [m]
  (if (map? m)
    (let [m (into {}
                  (map (fn [[k v]] [(camel-case k) v]))
                  m)]
      (cond-> m
              (map? (:style m))
              (update :style camel-case-keys)))
    m))

(defn element?
  "Return true if `x` is an HTML element. True when `x` is a vector
  and the first element is a keyword, e.g. `[:div]` or `[:div [:span \"x\"]`."
  [x]
  (and (vector? x)
       (keyword? (nth x 0))))

(defn html-to-dom-attrs
  "Converts all HTML attributes to their DOM equivalents."
  [attrs]
  #?(:clj (rename-keys (camel-case-keys attrs)
                       {:class :className
                        :for :htmlFor})
     :cljs (reduce-kv (fn [ret k v]
                        (if (= :style k)
                          (aset ret (str (camel-case k)) (html-to-dom-attrs v))
                          (aset ret (str (camel-case k)) v))
                        ret)
                      #js {}
                      attrs)))

(defn join-classes
  "Join the `classes` with a whitespace."
  [classes]
  (->> classes
       (into [] (comp
                  (mapcat (fn [x] (if (string? x) [x] (seq x))))
                  (remove nil?)))
       (str/join " ")))

(ns rum
  (:require
    [sablono.compiler :as s]))

(defn- -defc [mixins name argvec render]
  `(def ~name (rum/component (fn ~argvec ~(s/compile-html `(do ~@render)))
                            ~@(if (coll? mixins) mixins [mixins]))))

(defmacro defc [mixins name & rest]
  (if (vector? name)
    (-defc [] mixins name rest)
    (-defc mixins name (first rest) (next rest))))

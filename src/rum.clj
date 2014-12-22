(ns rum
  (:require
    [sablono.compiler :as s]))

(defmacro defc-raw [name argvec render]
  `(def ~name (rum/raw-component (fn ~argvec ~(s/compile-html render)))))

(defmacro defc-static [name argvec render]
  `(def ~name (rum/static-component (fn ~argvec ~(s/compile-html render)))))

(defmacro defc-reactive [name argvec render]
  `(def ~name (rum/reactive-component (fn ~argvec ~(s/compile-html render)))))



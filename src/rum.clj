(ns rum
  (:require
    [sablono.compiler :as s]))

(defmacro defraw [name argvec & render]
  `(def ~name (rum/raw-component (fn ~argvec ~(s/compile-html `(do ~@render))))))

(defmacro defstatic [name argvec & render]
  `(def ~name (rum/static-component (fn ~argvec ~(s/compile-html `(do ~@render))))))

(defmacro defreactive [name argvec & render]
  `(def ~name (rum/reactive-component (fn ~argvec ~(s/compile-html `(do ~@render))))))



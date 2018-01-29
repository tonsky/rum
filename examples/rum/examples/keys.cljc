(ns rum.examples.keys
  (:refer-clojure :exclude [keys])
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))


(rum/defc keyed
  < { :key-fn (fn [label number]
                (str label "-" number)) }
  [label number]
  [:div (str label "-" number)])


(rum/defc keys []
  [:div
    (list
      (keyed "a" 1)
      (keyed "a" 2)
      (keyed "b" 1)
      (rum/with-key (keyed "a" 1) "x"))])


#?(:cljs
(defn mount! [mount-el]
     (rum/hydrate (keys) mount-el)))

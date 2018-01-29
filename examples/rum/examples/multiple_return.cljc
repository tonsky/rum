(ns rum.examples.multiple-return
  (:require [rum.core :as rum]))


(rum/defc multiple-return []
  (for [n (range 5)]
    [:li {:key n} (str "Item #" n)]))


(rum/defc ulist [child]
  [:ul {}
   child])


#?(:cljs
(defn mount! [mount-el]
  (rum/hydrate (ulist (multiple-return)) mount-el)))

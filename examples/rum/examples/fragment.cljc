(ns rum.examples.fragment
  (:require [rum.core :as rum]))

(rum/defc fragment []
  [:*
   "This text"
   [:h2 "and this heading"]
   "are not wrapped in a parent element, only in a fragment."
   ])

#?(:cljs
(defn mount! [mount-el]
  (rum/hydrate (fragment) mount-el)))

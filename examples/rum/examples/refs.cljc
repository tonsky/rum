(ns rum.examples.refs
  (:require
   [rum.core :as rum]))

(rum/defc ta []
  (let [[value set-value!] (rum/use-state "Auto-resizing\ntextarea")]
    [:textarea
     {:style {:width   "100%"
              :padding "10px"
              :font    "inherit"
              :outline "none"
              :resize  "none"}
      :ref #(when %
              (set! (.-height (.-style %)) "0")
              (set! (.-height (.-style %)) (str (+ 2 (.-scrollHeight %)) "px")))
      :value value
      :placeholder "Auto-resizing textarea"
      :on-change #(set-value! (.. % -target -value))}]))

(rum/defc refs []
  [:div
   (ta)])

#?(:cljs
   (defn mount! [mount-el]
     (rum/hydrate (refs) mount-el)))

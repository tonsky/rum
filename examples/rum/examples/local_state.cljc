(ns rum.examples.local-state
  (:require
   [rum.core :as rum]
   [rum.examples.core :as core]))


;; Local component state


(rum/defcs local-state < (rum/local 0)
  [state title]
  (let [*count (:rum/local state)]
    [:div
     {:style {"-webkit-user-select" "none"
              "cursor" "pointer"}
      :on-click (fn [_] (swap! *count inc))}
     title ": " @*count]))

#?(:cljs
   (defn mount! [mount-el]
     (rum/hydrate (local-state "Clicks count") mount-el)))

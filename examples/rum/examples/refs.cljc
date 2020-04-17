(ns rum.examples.refs
  (:require
   [rum.core :as rum]))

(def ta-ref (rum/create-ref))

(rum/defcc ta
  < {:after-render
     (fn [state]
       (let [el (rum/deref ta-ref)
             _  (set! (.-height (.-style el)) "0")
             _  (set! (.-height (.-style el)) (str (+ 2 (.-scrollHeight el)) "px"))]
         state))}
  [comp]
  [:textarea
   {:ref ta-ref
    :style {:width   "100%"
            :padding "10px"
            :font    "inherit"
            :outline "none"
            :resize  "none"}
    :default-value "Auto-resizing\ntextarea"
    :placeholder "Auto-resizing textarea"
    :on-change (fn [_] (rum/request-render comp))}])

(rum/defc refs []
  [:div
   (ta)])

#?(:cljs
   (defn mount! [mount-el]
     (rum/hydrate (refs) mount-el)))

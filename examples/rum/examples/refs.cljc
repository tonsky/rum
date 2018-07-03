(ns rum.examples.refs
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))

(def ta-ref (rum/create-ref))

(rum/defcc ta
  < { :after-render
      (fn [state _]
        (let [ta (rum/current-ref-node ta-ref)]
          (set! (.-height (.-style ta)) "0")
          (set! (.-height (.-style ta)) (str (+ 2 (.-scrollHeight ta)) "px")))
        state) }
  [comp]
  [:textarea
    { :ref ta-ref
      :style { :width   "100%"
               :padding "10px"
               :font    "inherit"
               :outline "none"
               :resize  "none"}
      :default-value "Auto-resizing\ntextarea"
      :placeholder "Auto-resizing textarea"
      :on-change (fn [_] (rum/request-render comp)) }])


(rum/defc refs []
  [:div
    (ta)])


#?(:cljs
(defn mount! [mount-el]
     (rum/hydrate (refs) mount-el)))

(ns rum.examples.refs
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))


(rum/defcc ta < { :did-update
                  (fn [state]
                    (let [ta (rum/ref-node state "ta")]
                      (set! (.-height (.-style ta)) "0")
                      (set! (.-height (.-style ta)) (str (+ 2 (.-scrollHeight ta)) "px")))
                    state) }
  [comp]
  [:textarea
    { :ref "ta"
      :style { :width   "100%"
               :padding "10px"
               :font    "inherit"
               :outline "none"
               :height  "50px"
               :resize  "none"}
      :placeholder "Auto-resizeable textarea"
      :on-change (fn [_] (rum/request-render comp)) }])


(rum/defc refs []
  [:div
    (ta)])


#?(:cljs
(defn mount! [mount-el]
  (rum/mount (refs) mount-el)))

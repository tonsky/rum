(ns form
  (:require
    [clojure.string :as str]
    [rum]
    [utils :refer [el now clock color ts->str]]))

;; Form validation

(rum/defc validating-input < rum/reactive [ref fn]
  [:input {:type "text"
           :style {:width 170
                   :background-color (when-not (fn (rum/react ref))
                                       (rum/react color))}
           :value (rum/react ref)
           :on-change #(reset! ref (.. % -target -value))}])

(rum/defcs restricting-input < rum/reactive [state ref fn]
  (let [comp (:rum/react-component state)]
    [:input {:type "text"
             :style {:width 170}
             :value (rum/react ref)
             :on-change #(let [new-val (.. % -target -value)]
                           (if (fn new-val)
                             (reset! ref new-val)
                             ;; request-render is mandatory because sablono :input
                             ;; keeps current value in input’s state and always applies changes to it
                             (rum/request-render comp)))}]))

(rum/defcs restricting-input-native < rum/reactive [state ref fn]
  (let [comp (:rum/react-component state)]
    (js/React.DOM.input
      #js {:type "text"
           :style #js {:width 170}
           :value (rum/react ref)
           :onChange #(let [new-val (.. % -target -value)]
                         (when (fn new-val)
                           (reset! ref new-val))
                         ;; need forceUpdate here because otherwise rendering will be delayed until requestAnimationFrame 
                         ;; and that breaks cursor position inside input
                         (.forceUpdate comp))})))

(rum/defc val-form []
  (let [state (atom {:email "a@b.c"
                     :phone "+7913 000 0000"
                     :age   "22"})]
    [:dl
      [:dt "E-mail:"]
      [:dd (validating-input  (rum/cursor state [:email]) #(re-matches #"[^@]+@[^@.]+\..+" %))]
      [:dt "Phone:"]
      [:dd (restricting-input (rum/cursor state [:phone]) #(re-matches #"[0-9\- +()]*" %))]
      [:dt "Age:"]
      [:dd (restricting-input-native (rum/cursor state [:age]) #(re-matches #"([1-9][0-9]*)?" %))]]))

(rum/mount (val-form) (el "val-form"))

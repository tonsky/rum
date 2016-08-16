(ns rum.examples.inputs
  (:require
    [clojure.string :as str]
    [rum.core :as rum]))


(def values (range 1 5))


(rum/defc reactive-input < rum/reactive
  [*ref]
  (let [value (rum/react *ref)]
    [:input { :type "text"
              :value value
              :style { :width 170 }
              :on-change (fn [e] (reset! *ref (long (.. e -currentTarget -value)))) }]))


(rum/defc checkboxes < rum/reactive
  [*ref]
  (let [value (rum/react *ref)]
    [:div
      (for [v values]
        [:input { :type "checkbox"
                  :checked (= v value)
                  :value   v
                  :on-click (fn [_] (reset! *ref v)) }])]))
    

(rum/defc radio < rum/reactive
  [*ref]
  (let [value (rum/react *ref)]
    [:div
      (for [v values]
        [:input { :type "radio"
                  :name "inputs_radio"
                  :checked (= v value)
                  :value   v
                  :on-click (fn [_] (reset! *ref v)) }])]))


(rum/defc select < rum/reactive
  [*ref]
  (let [value (rum/react *ref)]
    [:select
      { :on-change (fn [e] (reset! *ref (long (.. e -target -value))))
        :value value }
      (for [v values]
        [:option { :value v } v])]))


(defn next-value [v]
  (loop [v' v]
    (if (= v v')
      (recur (rand-nth values))
      v')))


(rum/defc shuffle-button < rum/reactive
  [*ref]
  [:button
    { :on-click (fn [_] 
                  (swap! *ref next-value)) }
    "Next value"])


(rum/defc value < rum/reactive
  [*ref]
  [:code (pr-str (rum/react *ref))])


(rum/defc inputs []
  (let [*ref (atom nil)]
    [:dl
      [:dt "Input"]  [:dd (reactive-input *ref)]
      [:dt "Checks"] [:dd (checkboxes *ref)]
      [:dt "Radio"]  [:dd (radio *ref)]
      [:dt "Select"] [:dd (select *ref)]
      [:dt (value *ref)] [:dd (shuffle-button *ref)]]))


#?(:cljs
(defn mount! [mount-el]
  (rum/mount (inputs) mount-el)))



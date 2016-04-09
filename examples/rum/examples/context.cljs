(ns rum.examples.context
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))

;; Components with context that all descendants have access
;; to implicitly.

;; This is useful when you are using child components you
;; cannot modify. For example, a JS library that gives you
;; components which rely on a context value being set by an
;; ancestor component.

;; Assume the following component comes from a third party library
;; which relies on (.-context this).
(def child-from-lib-class
  (js/React.createClass #js {
    :contextTypes
    #js {:color js/React.PropTypes.string}
    :displayName
    "child-from-lib"
    :render
    (fn []
      (this-as this
        (js/React.createElement
          "div"
          #js {:style #js {:color (.. this -context -color)}}
          "Child component uses context to color font.")))}))


(defn child-from-lib-ctor []
  (rum/element child-from-lib-class {}))


;; Assume the following component is from our source code.
(def color-theme
  { :child-context (fn [state] {:color @core/*color}) 
    :class-properties { :childContextTypes {:color js/React.PropTypes.string} } })


(rum/defc context < color-theme []
  [:div
   [:div "Root component implicitly passes data to descendants."]
   (child-from-lib-ctor)])


(defn mount! [mount-el]
  (rum/mount (context) mount-el))

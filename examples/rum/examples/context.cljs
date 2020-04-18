(ns rum.examples.context
  (:require
   [rum.core :as rum]
   [rum.examples.core :as core]
   [cljsjs.prop-types]))

;; Components with context that all descendants have access to implicitly.

;; This is useful when you are using child components you cannot modify. 
;; For example, a JS library that gives you components which rely on a context
;; value being set by an ancestor component.


(rum/defcc rum-context-comp < {:static-properties {:contextTypes {:color js/PropTypes.string}}}
  [comp]
  [:div {:style {:color (.. comp -context -color)}}
   "(Class-based Context API) Child component uses context to set font color."])

(rum/defcontext *color-ctx*)

(rum/defc rum-create-context-comp []
  (rum/with-context [color *color-ctx*]
    [:div {:style {:color color}}
     "(Component-based Context API) Child component uses context to set font color."]))


;; Assume the following component is from our source code.


(def color-theme
  {:child-context (fn [state] {:color @core/*color})
   :static-properties {:childContextTypes {:color js/PropTypes.string}}})

(rum/defc context < color-theme []
  [:div
   [:div "Root component implicitly passes data to descendants."]
   (rum-context-comp)
   (rum/bind-context [*color-ctx* @core/*color]
                     (rum-create-context-comp))])

(defn mount! [mount-el]
  (rum/mount (context) mount-el))

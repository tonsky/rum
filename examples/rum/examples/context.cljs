(ns rum.examples.context
  (:require
    [rum.core :as rum]
    [sablono.core :refer [html]]
    [rum.examples.core :as core]))

;; Components with context that all descendants have access to implicitly.

;; https://reactjs.org/docs/context.html
;; Context is designed to share data that can be considered “global”
;; for a tree of React components,
;; such as the current authenticated user, theme, or preferred language.

(def color-theme
  (rum/create-context @core/*color))


(rum/defc rum-context-comp
  []
  (rum/with-context color-theme
    #(html [:span
            { :style { :color % }}
            "Child component uses context to set font color."])))

(rum/defc context < rum/reactive []
  [:div
    [:div "Root component implicitly passes data to descendants."]
    (rum/provide-context color-theme (rum/react core/*color) (rum-context-comp))])


(defn mount! [mount-el]
  (rum/mount (context) mount-el))

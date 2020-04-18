(ns rum.examples.js-components
  (:require [rum.core :as rum]))

#?(:cljs
   (defn h2 [props]
     (.createElement js/React "h2" nil (.-children props))))

(rum/defc js-components []
  [:div
   [:h1 "This is Rum component"]
   (rum/adapt-class h2 "This is JS component")])

#?(:clj
   (defn render-js-component [type-sym attrs children]
     ;; More realistic case would be running JS components serialization
     ;; in JS envs such as GraalJS
     (case type-sym
       'h2 (rum/render-static-markup (into [:h2 attrs] children))
       nil)))

(defn mount! [mount-el]
  #?(:cljs (rum/hydrate (js-components) mount-el)
     :clj (binding [rum/*render-js-component* render-js-component]
            (js-components))))

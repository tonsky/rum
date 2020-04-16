(ns sablono.core
  (:require [sablono.interpreter]
            [cljsjs.react]))

(defn create-element
  "The React.js create element function."
  [type attrs children]
  (.apply (.-createElement js/React) nil (.concat #js [type attrs] children)))

(def fragment
  "The React.js Fragment."
  (.-Fragment js/React))

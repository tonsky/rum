(ns daiquiri.core
  (:require [daiquiri.interpreter]
            [cljsjs.react]))

(defn create-element
  "The React.js create element function."
  [type attrs children]
  (if children
    (.apply (.-createElement js/React) nil (.concat #js [type attrs] children))
    (.createElement js/React type attrs)))

(def fragment
  "The React.js Fragment."
  (.-Fragment js/React))

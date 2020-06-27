(ns daiquiri.core
  (:require [daiquiri.interpreter]
            [cljsjs.react]))

(defn ^js/React.Element create-element
  "The React.js create element function."
  [type attrs children]
  (if ^boolean children
    (.apply (.-createElement js/React) nil (.concat #js [type attrs] children))
    (.createElement js/React type attrs)))

(def ^js/React.Fragment fragment
  "The React.js Fragment."
  (.-Fragment js/React))

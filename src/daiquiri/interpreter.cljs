(ns daiquiri.interpreter
  (:require [clojure.string :as str]
            [daiquiri.normalize :as normalize]
            [daiquiri.util :as util]
            [cljsjs.react]))

(defn ^js/React.Element create-element
  "Create a React element. Returns a JavaScript object when running
  under ClojureScript, and a om.dom.Element record in Clojure."
  [type attrs children]
  (.apply (.-createElement js/React) nil (.concat #js [type attrs] children)))

(defn attributes [attrs]
  (when-let [js-attrs (clj->js (util/html-to-dom-attrs attrs))]
    (let [class (.-className js-attrs)
          class (if (array? class) (str/join " " class) class)]
      (if (str/blank? class)
        (js-delete js-attrs "className")
        (set! (.-className js-attrs) class))
      js-attrs)))

(declare interpret)

(defn- ^array interpret-seq
  "Eagerly interpret the seq `x` as HTML elements."
  [x]
  (reduce
   (fn [^array ret x]
     (.push ret (interpret x))
     ret)
   #js []
   x))

(defn element
  "Render an element vector as a HTML element."
  [element]
  (let [[type attrs content] (normalize/element element)]
    (create-element type
                    (attributes attrs)
                    (interpret-seq content))))

(defn- interpret-vec
  "Interpret the vector `x` as an HTML element or a the children of an
  element."
  [x]
  (if (util/element? x)
    (element x)
    (interpret-seq x)))

(defn interpret [v]
  (cond
    (vector? v) (interpret-vec v)
    (seq? v) (interpret-seq v)
    :else v))

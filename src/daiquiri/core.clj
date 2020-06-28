(ns daiquiri.core
  (:require [daiquiri.compiler :as compiler]))

(defmacro attrs
  "Compile `attributes` map into a JavaScript literal."
  [attributes]
  (compiler/compile-attrs attributes))

(defmacro html
  "Compile the Hiccup `form`. Always produces code that evaluates to
  React elements."
  [form]
  (compiler/compile-html form &env))

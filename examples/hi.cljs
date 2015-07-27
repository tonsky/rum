(ns hi
  (:require
   [clojure.string :as str]
   [rum]))

(rum/defc greet [text]
  [:p (str "Hello, " text)])

(rum/mount (greet "world") (js/document.getElementById "hello"))

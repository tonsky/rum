(ns self-reference
  (:require
    [clojure.string :as str]
    [rum]
    [utils :refer [el now clock color ts->str speed]]))

;; Self-referencing component

(rum/defc tree < rum/static
  ([form] (tree form 0))
  ([form depth]
    (let [offset {:style {:margin-left (* 10 depth)}}]
      (if (sequential? form)
        [:.branch offset (map #(tree % (inc depth)) form)]
        [:.leaf   offset (str form)]))))

(rum/mount (tree [:a [:b [:c :d [:e] :g]]]) (el "selfie"))


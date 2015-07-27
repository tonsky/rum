(ns local-state
  (:require
    [clojure.string :as str]
    [rum]
    [utils :refer [el now clock color ts->str]]))

;; Local component state

(rum/defcs stateful < (rum/local 0) [{local :rum/local} title]
  [:div
   {:style {"-webkit-user-select" "none"
            "cursor" "pointer"}
    :on-click (fn [_] (swap! local inc))}
   title ": " @local])

(rum/mount (stateful "Clicks count") (el "local-state"))

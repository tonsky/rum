(ns react
  (:require
    [clojure.string :as str]
    [rum]
    [utils :refer [el now clock color ts->str]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Raw component (pure react-style)

(rum/defc forced-timer []
  [:div "Forced: "
    [:span {:style {:color @color}} (ts->str @clock)]])

(let [mount-el (el "forced-timer")
      comp     (rum/mount (forced-timer) mount-el)]
  ;; Setting up watch manually,
  ;; force specific component re-render via request-render
  (add-watch clock :forced-timer
    (fn [_ _ _ _]
      (rum/request-render comp))))

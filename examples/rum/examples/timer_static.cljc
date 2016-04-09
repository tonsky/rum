(ns rum.examples.timer-static
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Static component (quiescent-style)


(rum/defc timer-static < rum/static [label ts]
  [:div label ": "
    [:span {:style {:color @core/*color}} (core/format-time ts)]])


#?(:cljs
(defn mount! [mount-el]
  (rum/mount (timer-static "Static" @core/*clock) mount-el)
  ;; Setting up watch manually,
  ;; force top-down re-render via mount
  (add-watch core/*clock :timer-static
             (fn [_ _ _ new-val]
               (rum/mount (timer-static "Static" new-val) mount-el)))))

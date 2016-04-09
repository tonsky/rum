(ns rum.examples.timer-forced
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Raw component (pure react-style)


(rum/defc timer-forced []
  [:div "Forced: "
    [:span {:style {:color @core/*color}} (core/format-time @core/*clock)]])


#?(:cljs
(defn mount! [mount-el]
  (let [comp (rum/mount (timer-forced) mount-el)]
    ;; Setting up watch manually,
    ;; force specific component re-render via request-render
    (add-watch core/*clock :timer-forced
               (fn [_ _ _ _]
                 (rum/request-render comp))))))


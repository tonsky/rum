(ns rum.examples
  (:require
    [clojure.string :as str]
    [rum.core :as rum]
    [rum.examples.core :as core]
    
    [rum.examples.timer-static :as timer-static]
    [rum.examples.timer-reactive :as timer-reactive]
    [rum.examples.controls :as controls]
    [rum.examples.binary-clock :as binary-clock]
    [rum.examples.board-reactive :as board-reactive]
    [rum.examples.board-cursored :as board-cursored]
    [rum.examples.bmi-calculator :as bmi-calculator]
    [rum.examples.form-validation :as form-validation]
    [rum.examples.local-state :as local-state]
    [rum.examples.keys :as keys]
    [rum.examples.self-reference :as self-reference]
    [rum.examples.context :as context]
    [rum.examples.custom-props :as custom-props]))


(enable-console-print!)


;; Mount everything

(timer-static/mount!    (core/el "timer-static"))
(timer-reactive/mount!  (core/el "timer-reactive"))
(controls/mount!        (core/el "controls"))
(binary-clock/mount!    (core/el "binary-clock"))
(board-reactive/mount!  (core/el "board-reactive"))
(board-cursored/mount!  (core/el "board-cursored"))
(bmi-calculator/mount!  (core/el "bmi-calculator"))
(form-validation/mount! (core/el "form-validation"))
(local-state/mount!     (core/el "local-state"))
(keys/mount!            (core/el "keys"))
(self-reference/mount!  (core/el "self-reference"))
(context/mount!         (core/el "context"))
(custom-props/mount!    (core/el "custom-props")) 


;; Start clock ticking

(defn tick []
  (reset! core/*clock (.getTime (js/Date.)))
  (js/setTimeout tick @core/*speed))


(tick)

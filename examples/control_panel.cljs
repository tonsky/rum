(ns control-panel
  (:require
    [rum]
    [utils :refer [el now clock color ts->str speed]]
    [mixins]))

;; Using custom mixin
(rum/defc watches-count < mixins/autorefresh [ref]
  [:dd (count (.-watches ref)) " watches"])

;; Raw top-level component, everything interesting is happening inside
(rum/defc controls []
  [:dl
    [:dt "Color: "]
    [:dd (mixins/input color)]
;; Binding another component to the same atom will keep 2 input boxes in sync
    [:dt "Clone: "]
    [:dd (mixins/input color)]
    [:dt "Color: "]
    (watches-count color)
    [:dt "Tick: "]
    [:dd (mixins/input speed) " ms"]
    [:dt "Time:"]
    (watches-count clock)])

(rum/mount (controls) (el "controls"))


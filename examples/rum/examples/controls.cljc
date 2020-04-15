(ns rum.examples.controls
  (:require
   [rum.core :as rum]
   [rum.examples.core :as core]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Control panel


;; generic “atom editor” component


(rum/defc input < rum/reactive [ref]
  [:input {:type "text"
           :value (rum/react ref)
           :style {:width 100}
           :on-change #(reset! ref (.. % -target -value))}])


;; Raw top-level component, everything interesting is happening inside


(rum/defc controls []
  [:dl
   [:dt "Color: "]
   [:dd (input core/*color)]
    ;; Binding another component to the same atom will keep 2 input boxes in sync
   [:dt "Clone: "]
   [:dd (input core/*color)]
   [:dt "Color: "]
   [:dd (core/watches-count core/*color) " watches"]

   [:dt "Tick: "]
   [:dd (input core/*speed) " ms"]
   [:dt "Time:"]
   [:dd (core/watches-count core/*clock) " watches"]])

#?(:cljs
   (defn mount! [mount-el]
     (rum/hydrate (controls) mount-el)))


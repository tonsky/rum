(ns rum.examples.lifecycle
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))

(rum/defcs derived-state <
  {:derive-state
   (fn [{[color] :rum/args :as st}]
     (assoc st :derived/color color))}
  [{color :derived/color} _]
  [:div "derived state: " color])

(rum/defc snapshot <
  {:make-snapshot
   (fn [st]
     #?(:cljs (assoc st :rum/snapshot (.-innerWidth js/window))))
   :did-update
   (fn [st snapshot]
     (let [node (rum/ref-node st :snapshot)]
       (set! (.. node -textContent) (str "snapshot: " snapshot)))
     st)}
  []
  [:div {:ref :snapshot} "snapshot: "])

(rum/defc lifecycle < rum/reactive []
  [:div
   (derived-state (rum/react core/*color))
   (snapshot)])

#?(:cljs
   (defn mount! [mount-el]
     (rum/hydrate (lifecycle) mount-el)))

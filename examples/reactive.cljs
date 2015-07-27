(ns reactive
  (:require
    [clojure.string :as str]
    [rum]
    [utils :refer [el now clock color ts->str]]
    [mixins]))

;; Reactive drawing board

(def rboard (atom (mixins/random-board)))
(def rboard-renders (atom 0))

(rum/defc rcell < rum/reactive [x y]
  (swap! rboard-renders inc)
  (let [cursor (rum/cursor rboard [y x])]
    ;; each cell subscribes to its own cursor inside a board
    ;; note that subscription to color is conditional:
    ;; only if cell is on (@cursor == true),
    ;; this component will be notified on color changes
    [:div.art-cell {:style {:background-color (when (rum/react cursor) (rum/react color))}
                    :on-mouse-over (fn [_] (swap! cursor not) nil)}]))

(rum/defc art-rboard []
  [:div.artboard
    (for [y (range 0 utils/board-height)]
      [:div.art-row {:key y}
        (for [x (range 0 utils/board-width)]
          ;; this is how one can specify React key for component
          (rum/with-props rcell x y :rum/key [x y]))])
   (mixins/board-stats rboard rboard-renders)])

(rum/mount (art-rboard) (el "rboard"))

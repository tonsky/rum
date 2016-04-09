(ns rum.examples.board-reactive
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))


;; Reactive drawing board


(def *board (atom (core/initial-board)))
(def *board-renders (atom 0))


(rum/defc cell < rum/reactive [x y]
  (swap! *board-renders inc)
  (let [*cursor (rum/cursor *board [y x])]
    ;; each cell subscribes to its own cursor inside a board
    ;; note that subscription to color is conditional:
    ;; only if cell is on (@cursor == true),
    ;; this component will be notified on color changes
    [:div.art-cell {:style {:background-color (when (rum/react *cursor) (rum/react core/*color))}
                    :on-mouse-over (fn [_] (swap! *cursor not) nil)}]))


(rum/defc board-reactive []
  [:div.artboard
    (for [y (range 0 core/board-height)]
      [:div.art-row {:key y}
        (for [x (range 0 core/board-width)]
          ;; this is how one can specify React key for component
          (-> (cell x y)
              (rum/with-key [x y])))])
   (core/board-stats *board *board-renders)])


#?(:cljs
(defn mount! [mount-el]
  (rum/mount (board-reactive) mount-el)))

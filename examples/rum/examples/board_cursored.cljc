(ns rum.examples.board-cursored
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))


;; Cursor drawing board


(def *board (atom (core/initial-board)))
(def *board-renders (atom 0))


;; Cursored mixin will avoid re-rendering if cursors have not
;; changed their value since last rendering

(rum/defc cell < rum/cursored
  [x y *cursor]
  (swap! *board-renders inc)
  ;; note that color here is not passed via arguments
  ;; it means it will not be taken into account when deciding on re-rendering
  [:div.art-cell {:style {:background-color (when @*cursor @core/*color)}
                  :on-mouse-over (fn [_] (swap! *cursor not) nil)}])


;; cursored-watch mixin will setup watches for all IWatchable arguments
(rum/defc board-cursored < rum/cursored rum/cursored-watch
  [board]
  [:div.artboard
    (for [y (range 0 core/board-height)
          :let [*y-cursor (rum/cursor *board [y])]]
      [:div.art-row {:key y}
        (for [x (range 0 core/board-width)
              :let [*x-cursor (rum/cursor *y-cursor [x])]]
          (-> (cell x y *x-cursor)
              (rum/with-key [x y])))])
    (core/board-stats *board *board-renders)])


#?(:cljs
(defn mount! [mount-el]
  (rum/mount (board-cursored *board) mount-el)))

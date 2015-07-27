(ns cursor
  (:require
    [clojure.string :as str]
    [rum]
    [utils :refer [el now clock color ts->str]]
    [mixins]))

;; Cursor drawing board

(def board (atom (mixins/random-board)))
(def board-renders (atom 0))

;; Cursored mixin will avoid re-rendering if cursors have not
;; changed their value since last rendering

(rum/defc art-cell < rum/cursored [x y cursor]
  (swap! board-renders inc)
  ;; note that color here is not passed via arguments
  ;; it means it will not be taken into account when deciding on re-rendering
  [:div.art-cell {:style {:background-color (when @cursor @color)}
                  :on-mouse-over (fn [_] (swap! cursor not) nil)}])

;; cursored-watch mixin will setup watches for all IWatchable arguments
(rum/defc artboard < rum/cursored rum/cursored-watch [board]
  [:div.artboard
    (for [y (range 0 utils/board-height)
          :let [y-cursor (rum/cursor board [y])]]
      [:div.art-row {:key y}
        (for [x (range 0 utils/board-width)
              :let [x-cursor (rum/cursor y-cursor [x])]]
          (rum/with-props art-cell x y x-cursor :rum/key [x y]))])
    (mixins/board-stats board board-renders)])

(rum/mount (artboard board) (el "artboard"))

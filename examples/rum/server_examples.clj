(ns rum.server-examples
  (:gen-class)
  (:require
   [clojure.string :as str]
   [rum.core :as rum]))

(defn now []
  (.getTime (java.util.Date.)))

(def iso-format (java.text.SimpleDateFormat. "yyyy-MM-dd'T'HH:mm'Z'"))
(defn ts->str [ts]
  (let [date (java.util.Date. ts)
        str  (.format iso-format date)]
    (subs str 11 (dec (count str)))))

(def clock (atom (now)))
(def color (atom "#FA8D97"))
(def speed (atom 167))
(def bmi-data (atom {:height 180 :weight 80}))
(def autorefresh {})

(def to-run (atom []))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Static component (quiescent-style)

(rum/defc static-timer < rum/static [label ts]
  [:div label ": "
    [:span {:style {:color @color}} (ts->str ts)]])

(swap! to-run conj #(static-timer "Static" @clock))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Raw component (pure react-style)

(rum/defc forced-timer []
  [:div "Forced: "
    [:span {:style {:color @color}} (ts->str @clock)]])

(swap! to-run conj #(forced-timer))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reactive components (reagent-style)

;; regular static top-down component with immutable args
(rum/defc colored-clock < rum/static [time color]
  [:span {:style {:color color}} (ts->str time)])

(rum/defc reactive-timer < rum/reactive []
  [:div "Reactive: "
    ;; Subscribing to atom changes with rum/react
    ;; Then pass _values_ to static component
    (colored-clock (rum/react clock) (rum/react color))])

(swap! to-run conj #(reactive-timer))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reagent stype BMI calculator

(defn calc-bmi [{:keys [height weight bmi] :as data}]
  (let [h (/ height 100)]
    (if (nil? bmi)
      (assoc data :bmi (/ weight (* h h)))
      (assoc data :weight (* bmi h h)))))

(defn slider [param value min max]
  (let [reset (case param :bmi :weight :bmi)]
    [:input {:type "range" :value value :min min :max max
             :style {:width "100%"}
              :on-change #(swap! bmi-data assoc
                                param (-> % .-target .-value)
                                reset nil)}]))

(rum/defc bmi-component < rum/reactive []
  (let [{:keys [weight height bmi] :as data} (calc-bmi (rum/react bmi-data))
        [color diagnose] (cond
                          (< bmi 18.5) ["orange" "underweight"]
                          (< bmi 25) ["inherit" "normal"]
                          (< bmi 30) ["orange" "overweight"]
                          :else ["red" "obese"])]
    (reset! bmi-data data)
    [:div.bmi
      [:div
        "Height: " (int height) "cm"
        (slider :height height 100 220)]
      [:div
        "Weight: " (int weight) "kg"
        (slider :weight weight 30 150)]
      [:div
        "BMI: " (int bmi) " "
        [:span {:style {:color color}} diagnose]
        (slider :bmi bmi 10 50)]]))

(swap! to-run conj #(bmi-component))

;; Generic board utils

(def ^:const board-width 19)
(def ^:const board-height 10)

(defn random-board []
  (let [cell-fn #(> (rand) 0.9)
        row-fn  #(vec (repeatedly board-width cell-fn))]
    (vec (repeatedly board-height row-fn))))

(rum/defc board-stats < autorefresh rum/reactive [board renders]
  [:div.stats
    "Renders: "       (rum/react renders) [:br]
    "Board watches: " (count (.getWatches board)) [:br]
    "Color watches: " (count (.getWatches color))])

;; Reactive drawing board

(def rboard (atom (random-board)))
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
    (for [y (range 0 board-height)]
      [:div.art-row {:key y}
        (for [x (range 0 board-width)]
          ;; this is how one can specify React key for component
          (-> (rcell x y)
              (rum/with-key [x y])))])
   (board-stats rboard rboard-renders)])

(swap! to-run conj #(art-rboard))

;; Cursor drawing board

(def board (atom (random-board)))
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
    (for [y (range 0 board-height)
          :let [y-cursor (rum/cursor board [y])]]
      [:div.art-row {:key y}
        (for [x (range 0 board-width)
              :let [x-cursor (rum/cursor y-cursor [x])]]
          (-> (art-cell x y x-cursor)
              (rum/with-key [x y])))])
    (board-stats board board-renders)])

(swap! to-run conj #(artboard board))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Main (instead of mounting)

(defn -main [& args]
  (doseq [func @to-run]
    (pr (func))
    (println)))

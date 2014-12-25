(ns examples
  (:require
    [clojure.string :as str]
    [rum :include-macros true]))

(enable-console-print!)

(defn now []
  (.getTime (js/Date.)))

(defn ts->str [ts]
  (let [str (.toISOString (js/Date. ts))]
    (subs str 11 (dec (count str)))))

(def time (atom (now)))
(def color (atom "#FA8D97"))
(def speed (atom 126))

(defn tick []
  (reset! time (now))
  (js/setTimeout tick @speed))
(tick)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Static component (quiescent-style)

(rum/defc rum/static static-timer [label ts]
  [:div label ": "
    [:span {:style {:color @color}} (ts->str ts)]])
  
(defn ^:export start-static-timer [mount-el]
  (rum/mount (static-timer "Static" @time) mount-el)
  ;; Setting up watch manually, 
  ;; force top-down re-render via mount
  (add-watch time :static-timer
    (fn [_ _ _ new-val]
      (rum/mount (static-timer "Static" new-val) mount-el))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Raw component (pure react-style)

(rum/defc forced-timer []
  [:div "Forced: "
    [:span {:style {:color @color}} (ts->str @time)]])

(defn ^:export start-forced-timer [mount-el]
  (let [comp (rum/mount (forced-timer) mount-el)]
    ;; Setting up watch manually,
    ;; force specific component re-render via request-render
    (add-watch time :forced-timer
      (fn [_ _ _ _]
        (rum/request-render comp)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Reactive components (reagent-style)

;; regular static top-down component with immutable args
(rum/defc rum/static colored-clock [time color]
  [:span {:style {:color color}} (ts->str time)])

(rum/defc rum/reactive reactive-timer []
  [:div "Reactive: "
    ;; Subscribing to atom changes with rum/react
    ;; Then pass _values_ to static component
    (colored-clock (rum/react time) (rum/react color))])

(defn ^:export start-reactive-timer [mount-el]
  ;; After initial mount, all changes will be re-rendered automatically
  (rum/mount (reactive-timer) mount-el))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Control panel

;; generic “atom editor” component
(rum/defc rum/reactive input [ref]
  [:input {:type "text"
           :value (rum/react ref)
           :style {:width 100}
           :on-change #(reset! ref (-> % .-target .-value))}])

;; Custom mixin for updating components on timer
;; for cases where you have nothing to subscribe to
(def autorefresh {
  :did-mount (fn [state]
               (let [interval (js/setInterval #(rum/request-render (:rum/react-component state)) 1000)]
                 (assoc state ::interval interval)))
  :will-unmount (fn [state]
                  (js/clearInterval (::interval state)))
  })

;; Using custom mixin
(rum/defc autorefresh watches-count [ref]
  [:dd (count (.-watches ref)) " watches"])

;; Raw top-level component, everything interesting is happening inside
(rum/defc controls []
  [:dl
    [:dt "Color: "]
    [:dd (input color)]
    ;; Binding another component to the same atom will keep 2 input boxes in sync
    [:dt "Clone: "]
    [:dd (input color)]
    [:dt "Color: "]
    (watches-count color)

    [:dt "Tick: "]
    [:dd (input speed) " ms"]
    [:dt "Time:"]
    (watches-count time)
])

(defn ^:export start-controls [mount-el]
  (rum/mount (controls) mount-el))

;; Generic render-count label

(rum/defc rum/reactive render-count [ref]
  [:div.stats "Renders: " (rum/react ref)])

;; Binary clock

(def bclock-renders (atom 0))

(rum/defc rum/static bit [n bit]
  (swap! bclock-renders inc)
  [:td.bclock-bit {:style (when (bit-test n bit) {:background-color @color})}])

(rum/defc rum/reactive bclock []
  (let [date (js/Date. (rum/react time))
        hh   (quot (.getHours date) 10)
        hl   (mod  (.getHours date) 10)
        mh   (quot (.getMinutes date) 10)
        ml   (mod  (.getMinutes date) 10)
        sh   (quot (.getSeconds date) 10)
        sl   (mod  (.getSeconds date) 10)
        msh  (quot (.getMilliseconds date) 100)
        msm  (-> (.getMilliseconds date) (quot 10) (mod 10))
        msl  (mod (.getSeconds date) 10)]
    [:table.bclock
      [:tr [:td]      (bit hl 3) [:th] [:td]      (bit ml 3) [:th] [:td]      (bit sl 3) [:th] (bit msh 3) (bit msm 3) (bit msl 3)]
      [:tr [:td]      (bit hl 2) [:th] (bit mh 2) (bit ml 2) [:th] (bit sh 2) (bit sl 2) [:th] (bit msh 2) (bit msm 2) (bit msl 2)]
      [:tr (bit hh 1) (bit hl 1) [:th] (bit mh 1) (bit ml 1) [:th] (bit sh 1) (bit sl 1) [:th] (bit msh 1) (bit msm 1) (bit msl 1)]
      [:tr (bit hh 0) (bit hl 0) [:th] (bit mh 0) (bit ml 0) [:th] (bit sh 0) (bit sl 0) [:th] (bit msh 0) (bit msm 0) (bit msl 0)]
      [:tr [:th hh]   [:th hl]   [:th] [:th mh]   [:th ml]   [:th] [:th sh]   [:th sl]   [:th] [:th msh]   [:th msm]   [:th msl]]
      [:tr [:th {:colSpan 8}
             (render-count bclock-renders)]]]))

(defn ^:export start-binary-clock [mount-el]
  (rum/mount (bclock) mount-el))

;; Generic board utils

(def ^:const board-width 19)
(def ^:const board-height 10)

(defn random-board []
  (let [cell-fn #(> (rand) 0.9)
        row-fn  #(vec (repeatedly board-width cell-fn))]
    (vec (repeatedly board-height row-fn))))

(rum/defc [autorefresh rum/reactive] board-stats [board renders]
  [:div.stats
    "Renders: "       (rum/react renders) [:br]
    "Board watches: " (count (.-watches board)) [:br]
    "Color watches: " (count (.-watches color))])

;; Reactive drawing board

(def rboard (atom (random-board)))
(def rboard-renders (atom 0))

(rum/defc rum/reactive rcell [x y]
  (swap! rboard-renders inc)
  (let [cursor (rum/cursor rboard [y x])]
    ;; each cell subscribes to its own cursor inside a board
    ;; not that subscription to color is conditional:
    ;; only if cell is on (@cursor == true),
    ;; this component will be notified on color changes
    [:div.art-cell {:style {:background-color (when (rum/react cursor) (rum/react color))}
                    :on-mouse-over (fn [_] (swap! cursor not))}]))

(rum/defc art-rboard []
  [:div.artboard
    (for [y (range 0 board-height)]
      [:div.art-row
        (for [x (range 0 board-width)]
          (rcell x y))])
   (board-stats rboard rboard-renders)])

(defn ^:export start-rboard [mount-el]
  (rum/mount (art-rboard) mount-el))


;; Cursor drawing board

(def board (atom (random-board)))
(def board-renders (atom 0))

;; Cursored mixin will avoid re-rendering if cursors have not
;; changed its value since last rendering

(rum/defc rum/cursored art-cell [x y cursor]
  (swap! board-renders inc)
  ;; note that color here is not passed via arguments
  ;; it means it will not be taken into account when deciding on re-rendering
  [:div.art-cell {:style {:background-color (when @cursor @color)}
                  :on-mouse-over (fn [_] (swap! cursor not))}])

;; cursored-watch mixin will setup watches for all IWatchable arguments
(rum/defc [rum/cursored rum/cursored-watch] artboard [board]
  [:div.artboard
    (for [y (range 0 board-height)
          :let [y-cursor (rum/cursor board [y])]]
      [:div.art-row
        (for [x (range 0 board-width)
              :let [x-cursor (rum/cursor y-cursor [x])]]
          (art-cell x y x-cursor))])
    (board-stats board board-renders)])

(defn ^:export start-artboard [mount-el]
  (rum/mount (artboard board) mount-el))
  


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

(rum/defstatic static-timer [label ts]
  [:div label ": "
    [:span {:style {:color @color}} (ts->str ts)]])
  
(defn ^:export start-static-timer [mount-el]
  (rum/mount (static-timer "Time" @time) mount-el)
  ;; Setting up watch manually, 
  ;; force top-down re-render via mount
  (add-watch time :static-timer
    (fn [_ _ _ new-val]
      (rum/mount (static-timer "Time" new-val) mount-el))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Raw component (pure react-style)

(rum/defraw forced-timer []
  [:div "Time: "
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
(rum/defstatic colored-clock [time color]
  [:dd {:style {:color color}} (ts->str time)])

;; generic “atom editor” component
(rum/defreactive input [ref]
  [:input {:type "text"
           :value (rum/react ref)
           :style {:width 100}
           :on-change #(reset! ref (-> % .-target .-value))}])

(rum/defreactive reactive-timer []
  [:dl
    [:dt "Time: "]
    ;; rum/react is used instead of deref as it setup watches automatically
    ;; Value of derefed atoms will then be passed to static “colored-clock” component
    (colored-clock (rum/react time) (rum/react color))
    [:dt "Color: "]
    [:dd (input color)]
    ;; Binding another component to the same atom will keep 2 input boxes in sync
    [:dt "Clone: "]
    [:dd (input color)]
    ;; Binding another component to the same atom will keep 2 input boxes in sync
    [:dt "Tick: "]
    [:dd (input speed) " ms"]])

(defn ^:export start-reactive-timer [mount-el]
  ;; After initial mount, all changes will be re-rendered automatically
  (rum/mount (reactive-timer) mount-el))

;; Binary clock

(def bclock-renders (atom 0))

(rum/defstatic cell [n bit]
  (swap! bclock-renders inc)
  [:td.bclock-cell {:style (when (bit-test n bit) {:background-color @color})}])

(rum/defreactive bclock []
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
      [:tr [:td]       (cell hl 3) [:th] [:td]       (cell ml 3) [:th] [:td]       (cell sl 3) [:th] (cell msh 3) (cell msm 3) (cell msl 3)]
      [:tr [:td]       (cell hl 2) [:th] (cell mh 2) (cell ml 2) [:th] (cell sh 2) (cell sl 2) [:th] (cell msh 2) (cell msm 2) (cell msl 2)]
      [:tr (cell hh 1) (cell hl 1) [:th] (cell mh 1) (cell ml 1) [:th] (cell sh 1) (cell sl 1) [:th] (cell msh 1) (cell msm 1) (cell msl 1)]
      [:tr (cell hh 0) (cell hl 0) [:th] (cell mh 0) (cell ml 0) [:th] (cell sh 0) (cell sl 0) [:th] (cell msh 0) (cell msm 0) (cell msl 0)]
      [:tr [:th hh]    [:th hl]    [:th] [:th mh]    [:th ml]    [:th] [:th sh]    [:th sl]    [:th] [:th msh]    [:th msm]    [:th msl]]
      [:tr [:th {:colSpan 8} "Cells rendered: " (rum/react bclock-renders)]]]))

(defn ^:export start-binary-clock [mount-el]
  (rum/mount (bclock) mount-el))

;; Cursor drawing board

(def board-size-x 19)
(def board-size-y 11)
(defn random-board []
  (let [cell-fn #(rand-nth [true false])
        row-fn  #(vec (repeatedly board-size-x cell-fn))]
    (vec (repeatedly board-size-y row-fn))))

(def board (atom (random-board)))
(def board-renders (atom 0))

(rum/defreactive render-count [ref]
  [:div {:style {:line-height "40px"}} "Cells rendered: " (rum/react ref)])

(rum/defom art-cell [x y cursor]
  (swap! board-renders inc)
  [:div.art-cell {:style {:background-color (when @cursor @color)}
                  :on-mouse-over (fn [_] (swap! cursor not))}])

(rum/defreactive artboard []
  (rum/react board)
  [:div.artboard
    (for [y (range 0 board-size-y)
          :let [y-cursor (rum/cursor board [y])]]
      [:div.art-row
        (for [x (range 0 board-size-x)
              :let [x-cursor (rum/cursor y-cursor [x])]]
          (art-cell x y x-cursor))])
    (render-count board-renders)])

(defn ^:export start-artboard [mount-el]
  (rum/mount (artboard) mount-el))

;; Reactive drawing board

(def rboard (atom (random-board)))
(def rboard-renders (atom 0))

(rum/defreactive rcell [x y]
  (let [cursor (rum/cursor rboard [y x])]
    (swap! rboard-renders inc)
    [:div.art-cell {:style {:background-color (when (rum/react cursor) (rum/react color))}
                    :on-mouse-over (fn [_] (swap! cursor not))}]))

(rum/defraw art-rboard []
  [:div.artboard
    (for [y (range 0 board-size-y)]
      [:div.art-row
        (for [x (range 0 board-size-x)]
          (rcell x y))])
   (render-count rboard-renders)])

(defn ^:export start-rboard [mount-el]
  (rum/mount (art-rboard) mount-el))

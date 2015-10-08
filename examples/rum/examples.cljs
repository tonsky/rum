(ns rum.examples
  (:require
    [clojure.string :as str]
    [rum.core :as rum]))

(js/console.time "Init")

(enable-console-print!)

(defn now []
  (.getTime (js/Date.)))

(defn el [id] (js/document.getElementById id))

(defn ts->str [ts]
  (let [str (.toISOString (js/Date. ts))]
    (subs str 11 (dec (count str)))))

(def clock (atom (now)))
(def color (atom "#FA8D97"))
(def speed (atom 167))
(def bmi-data (atom {:height 180 :weight 80}))

(defn tick []
  (reset! clock (now))
  (js/setTimeout tick @speed))
(tick)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Static component (quiescent-style)

(rum/defc static-timer < rum/static [label ts]
  [:div label ": "
    [:span {:style {:color @color}} (ts->str ts)]])

(let [mount-el (el "static-timer")]
  (rum/mount (static-timer "Static" @clock) mount-el)
  ;; Setting up watch manually,
  ;; force top-down re-render via mount
  (add-watch clock :static-timer
    (fn [_ _ _ new-val]
      (rum/mount (static-timer "Static" new-val) mount-el))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Raw component (pure react-style)

(rum/defc forced-timer []
  [:div "Forced: "
    [:span {:style {:color @color}} (ts->str @clock)]])

(let [mount-el (el "forced-timer")
      comp     (rum/mount (forced-timer) mount-el)]
  ;; Setting up watch manually,
  ;; force specific component re-render via request-render
  (add-watch clock :forced-timer
    (fn [_ _ _ _]
      (rum/request-render comp))))


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

;; After initial mount, all changes will be re-rendered automatically
(rum/mount (reactive-timer) (el "reactive-timer"))


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

;; After initial mount, all changes will be re-rendered automatically
(rum/mount (bmi-component) (el "reactive-bmi-calculator"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Control panel

;; generic “atom editor” component
(rum/defc input < rum/reactive [ref]
  [:input {:type "text"
           :value (rum/react ref)
           :style {:width 100}
           :on-change #(reset! ref (.. % -target -value))}])

;; Custom mixin for updating components on timer
;; for cases where you have nothing to subscribe to
(def autorefresh {
  :did-mount (fn [state]
               (let [interval (js/setInterval #(rum/request-render (:rum/react-component state)) 1000)]
                 (assoc state ::interval interval)))
  :transfer-state (fn [old-state state]
                    (merge state (select-keys old-state [::interval])))
  :will-unmount (fn [state]
                  (js/clearInterval (::interval state)))
  })

;; Using custom mixin
(rum/defc watches-count < autorefresh [ref]
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
    (watches-count clock)
])

(rum/mount (controls) (el "controls"))

;; Generic render-count label

(rum/defc render-count < rum/reactive [ref]
  [:div.stats "Renders: " (rum/react ref)])

;; Binary clock

(def bclock-renders (atom 0))

(rum/defc bit < rum/static [n bit]
  (swap! bclock-renders inc)
  [:td.bclock-bit {:style (when (bit-test n bit) {:backgroundColor @color})}])

(rum/defc bclock < rum/reactive []
  (let [date (js/Date. (rum/react clock))
        hh   (quot (.getHours date) 10)
        hl   (mod  (.getHours date) 10)
        mh   (quot (.getMinutes date) 10)
        ml   (mod  (.getMinutes date) 10)
        sh   (quot (.getSeconds date) 10)
        sl   (mod  (.getSeconds date) 10)
        msh  (quot (.getMilliseconds date) 100)
        msm  (->   (.getMilliseconds date) (quot 10) (mod 10))
        msl  (mod  (.getMilliseconds date) 10)]
    [:table.bclock
      [:tr [:td]      (bit hl 3) [:th] [:td]      (bit ml 3) [:th] [:td]      (bit sl 3) [:th] (bit msh 3) (bit msm 3) (bit msl 3)]
      [:tr [:td]      (bit hl 2) [:th] (bit mh 2) (bit ml 2) [:th] (bit sh 2) (bit sl 2) [:th] (bit msh 2) (bit msm 2) (bit msl 2)]
      [:tr (bit hh 1) (bit hl 1) [:th] (bit mh 1) (bit ml 1) [:th] (bit sh 1) (bit sl 1) [:th] (bit msh 1) (bit msm 1) (bit msl 1)]
      [:tr (bit hh 0) (bit hl 0) [:th] (bit mh 0) (bit ml 0) [:th] (bit sh 0) (bit sl 0) [:th] (bit msh 0) (bit msm 0) (bit msl 0)]
      [:tr [:th hh]   [:th hl]   [:th] [:th mh]   [:th ml]   [:th] [:th sh]   [:th sl]   [:th] [:th msh]   [:th msm]   [:th msl]]
      [:tr [:th {:colSpan 8}
             (render-count bclock-renders)]]]))

(rum/mount (bclock) (el "binary-timer"))

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
    "Board watches: " (count (.-watches board)) [:br]
    "Color watches: " (count (.-watches color))])

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

(rum/mount (art-rboard) (el "rboard"))


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

(rum/mount (artboard board) (el "artboard"))


;; Form validation

(rum/defc validating-input < rum/reactive [ref fn]
  [:input {:type "text"
           :style {:width 170
                   :background-color (when-not (fn (rum/react ref))
                                       (rum/react color))}
           :value (rum/react ref)
           :on-change #(reset! ref (.. % -target -value))}])

(rum/defcc restricting-input < rum/reactive [comp ref fn]
  [:input {:type "text"
           :style {:width 170}
           :value (rum/react ref)
           :on-change #(let [new-val (.. % -target -value)]
                         (if (fn new-val)
                           (reset! ref new-val)
                           ;; request-render is mandatory because sablono :input
                           ;; keeps current value in input’s state and always applies changes to it
                           (rum/request-render comp)))}])

(rum/defcs restricting-input-native < rum/reactive [state ref fn]
  (let [comp (:rum/react-component state)]
    (js/React.DOM.input
      #js {:type "text"
           :style #js {:width 170}
           :value (rum/react ref)
           :onChange #(let [new-val (.. % -target -value)]
                         (when (fn new-val)
                           (reset! ref new-val))
                         ;; need forceUpdate here because otherwise rendering will be delayed until requestAnimationFrame 
                         ;; and that breaks cursor position inside input
                         (.forceUpdate comp))})))

(rum/defc val-form []
  (let [state (atom {:email "a@b.c"
                     :phone "+7913 000 0000"
                     :age   "22"})]
    [:dl
      [:dt "E-mail:"]
      [:dd (validating-input  (rum/cursor state [:email]) #(re-matches #"[^@]+@[^@.]+\..+" %))]
      [:dt "Phone:"]
      [:dd (restricting-input (rum/cursor state [:phone]) #(re-matches #"[0-9\- +()]*" %))]
      [:dt "Age:"]
      [:dd (restricting-input-native (rum/cursor state [:age]) #(re-matches #"([1-9][0-9]*)?" %))]]))

(rum/mount (val-form) (el "val-form"))

;; Local component state

(rum/defcs stateful < (rum/local 0) [{local :rum/local} title]
  [:div
   {:style {"-webkit-user-select" "none"
            "cursor" "pointer"}
    :on-click (fn [_] (swap! local inc))}
   title ": " @local])

(rum/mount (stateful "Clicks count") (el "local-state"))

;; Self-referencing component

(rum/defc tree < rum/static
  ([form] (tree form 0))
  ([form depth]
    (let [offset {:style {:margin-left (* 10 depth)}}]
      (if (sequential? form)
        [:.branch offset (map #(tree % (inc depth)) form)]
        [:.leaf   offset (str form)]))))

(rum/mount (tree [:a [:b [:c :d [:e] :g]]]) (el "selfie"))


;; Components with context that all descendants have access
;; to implicitly.

;; This is useful when you are using child components you
;; cannot modify. For example, a JS library that gives you
;; components which rely on a context value being set by an
;; ancestor component.

;; Assume the following component comes from a third party library
;; which relies on (.-context this).
(def child-from-lib-class
  (js/React.createClass #js {
    :contextTypes
    #js {:color js/React.PropTypes.string}
    :displayName
    "child-from-lib"
    :render
    (fn []
      (this-as this
        (js/React.createElement
          "div"
          #js {:style #js {:color (.. this -context -color)}}
          "Child component uses context to color font.")))}))

(defn child-from-lib-ctor []
  (rum/element child-from-lib-class {}))

;; Assume the following component is from our source code.
(def color-theme
  { :child-context (fn [state] {:color @color}) 
    :class-properties { :childContextTypes {:color js/React.PropTypes.string} } })

(rum/defc our-src < color-theme []
  [:div
   [:div "Root component implicitly passes data to descendants."]
   (child-from-lib-ctor)])

(rum/mount (our-src) (el "context"))


;; Custom methods and data on the underlying React components.

(def custom-props
  {:msgData "Components can store custom data on the underlying React component."
   :msgMethod #(this-as this
                 [:div {:style {:cursor "pointer"}
                        :on-mouse-move
                        (fn [_]
                          (aset this "msgData" (rand))
                          (rum/request-render this))}
                  "Custom methods too. Hover me!"])})

(rum/defcc custom < {:class-properties custom-props} [this]
  [:div
   ;; using aget to avoid writing externs
   [:div (aget this "msgData")]
   [:div ((aget this "msgMethod"))]])

(rum/mount (custom) (el "custom"))

(js/console.timeEnd "Init")

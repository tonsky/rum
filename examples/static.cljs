(ns static
  (:require
    [clojure.string :as str]
    [rum]
    [utils :refer [el now clock color ts->str]]))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Regular static top-down component with immutable args

(rum/defc colored-clock < rum/static [time color]
  [:span {:style {:color color}} (ts->str time)])

(rum/defc reactive-timer < rum/reactive []
  [:div "Reactive: "
    ;; Subscribing to atom changes with rum/react
    ;; Then pass _values_ to static component
    (colored-clock (rum/react clock) (rum/react color))])

;; After initial mount, all changes will be re-rendered automatically
(rum/mount (reactive-timer) (el "reactive-timer"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Compose multiple static components, a Binary clock example

(rum/defc render-count < rum/reactive [ref]
  [:div.stats "Renders: " (rum/react ref)])

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

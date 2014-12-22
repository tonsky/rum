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
(def color (atom "#FA3D97"))

(js/setInterval #(reset! time (now)) 783)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Static component (quiescent-style)

(rum/defc-static static-timer [label ts]
  [:div (str label ": " (ts->str ts))])
  
(defn ^:export start-static-timer [mount-el]
  (rum/mount (static-timer "Time" @time) mount-el)
  ;; Setting up watch manually, 
  ;; force top-down re-render via mount
  (add-watch time :static-timer
    (fn [_ _ _ new-val]
      (rum/mount (static-timer "Time" new-val) mount-el))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Raw component (pure react-style)

(rum/defc-raw forced-timer []
  [:div (str "Time: " (ts->str @time))])

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
(rum/defc-static colored-clock [time color]
  [:dd {:style {:color color}} (ts->str time)])

;; generic “atom editor” component
(rum/defc-reactive input [ref]
  [:input {:type "text"
           :value (rum/react ref)
           :style {:width 100}
           :on-change #(reset! ref (-> % .-target .-value))}])

(rum/defc-reactive reactive-timer []
  [:dl
    [:dt "Time: "]
    ;; Using rum/react instead of deref will setup watches automatically
    ;; Value of derefed atoms will then is passed to static “clock” component
    (colored-clock (rum/react time) (rum/react color))
    [:dt "Color: "]
    [:dd (input color)]
    ;; Binding another component to the same atom will keep 2 input boxes in sync
    [:dt "Clone: "]
    [:dd (input color)]])

(defn ^:export start-reactive-timer [mount-el]
  ;; After initial mount, all changes will be re-rendered automatically
  (rum/mount (reactive-timer) mount-el))

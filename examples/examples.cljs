(ns examples
  (:require
    [clojure.string :as str]
    [rum :include-macros true]
    [sablono.core :as s :include-macros true]))

(enable-console-print!)

(defn now []
  (.getTime (js/Date.)))

(def time (atom 0))
(js/setInterval #(reset! time (now)) 247)
(def color (atom "#FA3D97"))

(defn ts->str [ts]
  (let [str (.toISOString (js/Date. ts))]
    (subs str 11 (dec (count str)))))

(defn ^:export start-static-timer [mount-el]
  (let [ctor (rum/static-component
               (fn [_ label ts]
                 (s/html
                   [:div (str label ": " (ts->str ts))])))]
    ;; Setting up watch manually, force top-down re-render via mount
    (add-watch time :static-timer
      (fn [_ _ _ new-val]
        (rum/mount (ctor "Time" new-val) mount-el)))))

(defn ^:export start-forced-timer [mount-el]
  (let [ctor (rum/static-component
               (fn [_]
                 (s/html
                   [:div (str "Time: " (ts->str @time))])))
        comp (rum/mount (ctor) mount-el)]
    ;; Setting up watch manually, force specific component re-render via request-render
    (add-watch time :forced-timer
      (fn [_ _ _ _]
        (rum/request-render comp)))))

(defn ^:export start-reactive-timer [mount-el]
  (let [;; regular static top-down component with immutable args
        clock (rum/static-component
                (fn [_ time color]
                  (s/html
                    [:dd {:style {:color color}} (ts->str time)])))

        ;; generic “atom editor” component
        input (rum/reactive-component
                (fn [_ atom]
                  (s/html
                    [:input {:type "text"
                             :value (rum/react atom)
                             :style {:width 100}
                             :on-change #(reset! atom (-> % .-target .-value str/upper-case))}])))
        
        ctor (rum/reactive-component (fn [_]
               (s/html
                 [:dl
                   [:dt "Time: "]
                   ;; Using rum/react instead of deref will setup watches automatically
                   ;; Value of derefed atoms will then is passed to static “clock” component
                   (clock (rum/react time) (rum/react color))
                   [:dt "Color: "]
                   [:dd (input color)]
                   ;; Binding another component to the same atom will keep 2 input boxes in sync
                   [:dt "Clone: "]
                   [:dd (input color)]])))]
    
    ;; After initial mount, all changes will be re-rendered automatically
    (rum/mount (ctor) mount-el)))

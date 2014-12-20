(ns examples
  (:require
    [rum :include-macros true]
    [sablono.core :as s :include-macros true]))

(enable-console-print!)

(defn now []
  (.getTime (js/Date.)))

(def time (atom 0))
(js/setInterval #(reset! time (now)) 247)

(defn ts->str [ts]
  (let [str (.toISOString (js/Date. ts))]
    (subs str 11 (dec (count str)))))

(defn ^:export start-static-timer [mount-el]
  (let [ctor (rum/static-component
               (fn [_ label ts]
                 (s/html
                   [:div (str label ": " (ts->str ts))])))]
    (add-watch time :static-timer
      (fn [_ _ _ new-val]
        (rum/mount (ctor "Time" new-val) mount-el)))))

(defn ^:export start-forced-timer [mount-el]
  (let [ctor (rum/static-component
               (fn [_]
                 (s/html
                   [:div (str "Time: " (ts->str @time))])))
        comp (rum/mount (ctor) mount-el)]
    (add-watch time :forced-timer
      (fn [_ _ _ _]
        (rum/request-render comp)))))

(defn ^:export start-reactive-timer [mount-el]
  (let [ctor (rum/reactive-component (fn [_]
               (s/html
                 [:div (str "Time: " (ts->str (rum/react time)))])))]
    (rum/mount (ctor) mount-el)))


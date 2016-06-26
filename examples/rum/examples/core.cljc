(ns rum.examples.core
  (:require
    [rum.core :as rum]))


(def *clock (atom 0))
(def *color (atom "#FA8D97"))
(def *speed (atom 167))


#?(:clj
    (def formatter
      (doto (java.text.SimpleDateFormat. "HH:mm:ss.SSS")
        (.setTimeZone (java.util.TimeZone/getTimeZone "UTC")))))


(defn format-time [ts]
  #?(:cljs (-> ts (js/Date.) (.toISOString) (subs 11 23))
     :clj  (.format formatter (java.util.Date. ts))))


#?(:cljs
    (defn el [id]
      (js/document.getElementById id)))


;; Custom mixin for updating components on timer
;; for cases where you have nothing to subscribe to
(defn periodic-refresh [period]
  #?(:cljs 
      { :did-mount 
        (fn [state]
          (let [react-comp (:rum/react-component state)
                interval   (js/setInterval #(rum/request-render react-comp) period)]
            (assoc state ::interval interval)))        
        :will-unmount
        (fn [state]
          (js/clearInterval (::interval state))) }
     :clj {}))


;; Using custom mixin
(rum/defc watches-count < (periodic-refresh 1000) [ref]
  [:span (count #?(:cljs (.-watches ref)
                   :clj  (.getWatches ^clojure.lang.IRef ref))) ])


;; Generic board utils


(def ^:const board-width 19)
(def ^:const board-height 10)


(defn prime? [i]
  (and (>= i 2)
       (empty? (filter #(= 0 (mod i %)) (range 2 i)))))


(defn initial-board []
  (->> (map prime? (range 0 (* board-width board-height)))
       (partition board-width)
       (mapv vec)))


(rum/defc board-stats < rum/reactive [*board *renders]
  [:div.stats
    "Renders: "       (rum/react *renders)
    [:br]
    "Board watches: " (watches-count *board)
    [:br]
    "Color watches: " (watches-count *color) ])

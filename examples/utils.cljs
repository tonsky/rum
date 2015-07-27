(ns utils
  (:require
    [clojure.string :as str]))

(enable-console-print!)

(defn el [id]
  (js/document.getElementById id))

(defn now []
  (.getTime (js/Date.)))

(def clock (atom (now)))
(def color (atom "#FA8D97"))
(def speed (atom 167))

(defn tick []
  (reset! clock (now))
  (js/setTimeout tick @speed))

(tick)

;; Generic board utils
(def ^:const board-width 19)
(def ^:const board-height 10)

(defn ts->str [ts]
  (let [str (.toISOString (js/Date. ts))]
    (subs str 11 (dec (count str)))))


(ns ^:no-doc rum.util)


(defn collect [key mixins]
  (->> (map (fn [m] (get m key)) mixins)
       (remove nil?)))


(defn collect* [keys mixins]
  (->> (mapcat (fn [m] (map (fn [k] (get m k)) keys)) mixins)
       (remove nil?)))


(defn call-all [state fns & args]
  (reduce
    (fn [state fn]
      (apply fn state args))
    state
    fns))
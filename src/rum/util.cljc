(ns ^:no-doc rum.util)


(defn collect [key mixins]
  (into []
        (keep (fn [m] (get m key)))
        mixins))


(defn collect* [keys mixins]
  (into []
        (mapcat (fn [m] (keep (fn [k] (get m k)) keys)))
        mixins))


(defn call-all [state fns & args]
  (reduce
    (fn [state fn]
      (apply fn state args))
    state
    fns))
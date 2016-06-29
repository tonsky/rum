(ns rum.util)


(defn collect [fn-key classes]
  (->> classes
       (map fn-key)
       (remove nil?)))


(defn call-all [state fns & args]
  (reduce
    (fn [state fn]
      (apply fn state args))
    state
    fns))

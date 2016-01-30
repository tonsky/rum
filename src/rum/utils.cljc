(ns rum.utils)

(let [last-id (volatile! 0)]
  (defn next-id []
    (vswap! last-id inc)))

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

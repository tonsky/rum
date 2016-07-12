(ns rum.derived-atom)


(defn derived-atom
  ([refs key f]
    (derived-atom refs key f {}))
  ([refs key f opts]
    (let [{ :keys [ref check-equals?]
            :or { check-equals? true }} opts
          recalc (case (count refs)
                   1 (let [[a] refs] #(f @a))
                   2 (let [[a b] refs] #(f @a @b))
                   3 (let [[a b c] refs] #(f @a @b @c))
                   #(apply f (map deref refs)))
          sink   (if ref
                   (doto ref (reset! (recalc)))
                   (atom (recalc)))
          watch  (if check-equals?
                   (fn [_ _ _ _]
                     (let [new-val (recalc)]
                       (when (not= @sink new-val)
                         (reset! sink new-val))))
                   (fn [_ _ _ _]
                     (reset! sink (recalc))))]
      (doseq [ref refs]
        (add-watch ref key watch))
      sink)))

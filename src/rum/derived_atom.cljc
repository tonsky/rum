(ns rum.derived-atom)


(defn derived-atom
  "Use this to create “chains” and acyclic graphs of dependent atoms.
   `derived-atom` will:
    - Take N “source” refs
    - Set up a watch on each of them
    - Create “sink” atom
    - When any of source refs changes:
       - re-run function `f`, passing N dereferenced values of source refs
       - `reset!` result of `f` to the sink atom
    - return sink atom
  
    (def *a (atom 0))
    (def *b (atom 1))
    (def *x (derived-atom [*a *b] ::key
              (fn [a b]
                (str a \":\" b))))
    (type *x) ;; => clojure.lang.Atom
    \\@*x     ;; => 0:1
    (swap! *a inc)
    \\@*x     ;; => 1:1
    (reset! *b 7)
    \\@*x     ;; => 1:7
  
   Arguments:
     refs - sequence of source refs
     key  - unique key to register watcher, see `clojure.core/add-watch`
     f    - function that must accept N arguments (same as number of source refs)
            and return a value to be written to the sink ref.
            Note: `f` will be called with already dereferenced values
     opts - optional. Map of:
       :ref           - Use this as sink ref. By default creates new atom
       :check-equals? - Do an equality check on each update: `(= @sink (f new-vals))`.
                        If result of `f` is equal to the old one, do not call `reset!`.
                        Defaults to `true`. Set to false if calling `=` would be expensive"
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

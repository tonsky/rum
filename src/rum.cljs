(ns rum)

(enable-console-print!)

(let [last-id (volatile! 0)]
  (defn next-id []
    (vswap! last-id inc)))

(def schedule
  (or js/window.requestAnimationFrame
      js/window.webkitRequestAnimationFrame
      js/window.mozRequestAnimationFrame
      js/window.msRequestAnimationFrame
      #(js/setTimeout % 16)))

(def ^:dynamic *component*)

(defn prop [comp name]
  (aget (.-props comp) name))

(defn id    [comp] (prop comp "__rum_id"))
(defn state [comp] (prop comp "__rum_state"))
(defn args  [comp] (prop comp "__rum_args"))

(defn component [render-fn & {:keys [state should-update? will-unmount]}]
  (let [class
        (js/React.createClass
           #js {:render
                (fn []
                  (this-as this
                    (binding [*component* this]
                      (apply render-fn this (args this)))))
                :shouldComponentUpdate
                (fn [next-props next-state]
                  (if false ;; should-update?
                    (this-as this
                      (binding [*component* this]
                        (should-update? this next-props next-state)))
                    true))
                :componentWillUnmount
                (fn []
                  (when will-unmount
                    (this-as this
                      (binding [*component* this]
                        (will-unmount this)))))
                })]
    (fn [& args]
      (js/React.createElement class
        #js {:__rum_id    (next-id)
             :__rum_state state
             :__rum_args  args}))))

(defn static-component [render-fn]
  (component render-fn
    :should-update?
    (fn [comp next-props next-state]
      (not= (args comp)
            (aget next-props "__rum_args")))))

(defn compare-by [keyfn]
  (fn [x y]
    (compare (keyfn x) (keyfn y))))

(def empty-queue (sorted-set-by (compare-by id)))
(def render-queue (volatile! empty-queue))

(defn render []
  (let [queue @render-queue]
    (vreset! render-queue empty-queue)
    (doseq [comp queue]
      (.forceUpdate comp))))

(defn request-render [component]
  (when (empty? @render-queue)
    (schedule render))
  (vswap! render-queue conj component))

(defn mount [component node]
  (js/React.render component node))

;; Reactive components

(def ^:dynamic *reactions*)

(defn reactive-component [render-fn]
  (component
    (fn [comp & args]
      (binding [*reactions* (volatile! #{})]
        (let [dom           (apply render-fn comp args)
              old-reactions @(state comp)
              new-reactions @*reactions*
              key           (id comp)]
          (doseq [atom old-reactions]
            (when-not (contains? new-reactions atom)
              (remove-watch atom key)))
          (doseq [atom new-reactions]
            (when-not (contains? old-reactions atom)
              (add-watch atom key
                (fn [_ _ _ _]
                  (request-render comp)))))
          (vreset! (state comp) new-reactions)
          dom)))
    :state (volatile! #{})
    :should-update?
    (fn [comp next-props next-state]
      (not= (args comp)
            (aget next-props "__rum_args")))
    :will-unmount
    (fn [comp]
      (let [key (id comp)]
        (doseq [atom @(state comp)]
          (remove-watch atom key))))))

(defn react [a]
  (vswap! *reactions* conj a)
  @a)

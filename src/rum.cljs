(ns rum)

(enable-console-print!)

(let [id (volatile! 0)]
  (defn next-id []
    (vswap! id inc)))

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

(defn component [render-fn & {:keys [should-update? will-unmount]}]
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
             :__rum_state (volatile! {})
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

(defn reactive-component [render-fn]
  (component render-fn
    :will-unmount
    (fn [comp]
      (let [id (id comp)]
        (doseq [atom (:reactive-atoms @(state comp))]
          (remove-watch atom id))))))

(defn react [a]
  (let [comp  *component*
        state (state comp)]
    (when-not (contains? (:reactive-atoms @state) a)
      (vswap! state update :reactive-atoms (fnil conj #{}) a)
      (add-watch a (id comp)
        (fn [_ _ _ _]
          (request-render comp))))
    @a))

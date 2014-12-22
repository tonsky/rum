(ns rum
  (:require
    sablono.core))

(enable-console-print!)

(let [last-id (volatile! 0)]
  (defn next-id []
    (vswap! last-id inc)))

(defn state [comp]
  (aget (.-props comp) ":rum/state"))

(defn update-state! [comp f & args]
  (let [state (state comp)]
    (vreset! state (apply f @state args))))

(defn id [comp]
  (::id @(state comp)))

(defn- fns [fn-key classes]
  (->> classes
       (map fn-key)
       (remove nil?)))

(defn- call-all [state fns & args]
  (reduce
    (fn [state fn]
      (apply fn state args))
    state
    fns))

(defn build-class [& classes]
  (let [will-mount    (fns :will-mount classes)
        did-mount     (fns :did-mount classes)
        should-update (fns :should-update classes)
        render        (reverse (fns :render classes))
        will-unmount  (fns :will-unmount classes)
        ctor
          (js/React.createClass #js {
            :getInitialState
            (fn []
              (this-as this
                (update-state! this assoc ::component this)))
            :componentWillMount
            (when-not (empty? will-mount)
              (fn []
                (this-as this
                  (update-state! this call-all will-mount))))
            :componentDidMount
            (when-not (empty? did-mount)
              (fn []
                (this-as this
                  (update-state! this call-all did-mount))))
            :componentWillReceiveProps
            (fn [next-props]
              (this-as this
                (vswap! (aget next-props ":rum/state") assoc ::component this)))
            :shouldComponentUpdate
            (if (empty? should-update)
              (constantly true)
              (fn [next-props next-state]
                (this-as this
                  (let [old-state @(state this)
                        new-state @(aget next-props ":rum/state")]
                    (or (some #(% old-state new-state) should-update) false)))))
;;             :componentWillUpdate (fn [next-props next-state])
            :render
            (fn []
              (this-as this
                ((first render) (next render) @(state this))))
;;             :componentDidUpdate (fn [prev-props prev-state])
            :componentWillUnmount
            (when-not (empty? will-unmount)
              (fn []
                (this-as this
                  (update-state! this call-all will-unmount))))})]
    (fn [& args]
      (js/React.createElement ctor
          #js {":rum/state" (volatile! {::args args
                                        ::id (next-id)})}))))

;; render queue

(def schedule
  (or js/window.requestAnimationFrame
      js/window.webkitRequestAnimationFrame
      js/window.mozRequestAnimationFrame
      js/window.msRequestAnimationFrame
      #(js/setTimeout % 16)))

(defn compare-by [keyfn]
  (fn [x y]
    (compare (keyfn x) (keyfn y))))

(def empty-queue (sorted-set-by (compare-by id))) ;; sorted by mount order, top to bottom
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

;; render mixin

(defn render-mixin [render-fn]
  {:render (fn [_ state] (apply render-fn (::args state)))})

;; raw component

(defn raw-component [render-fn]
  (build-class (render-mixin render-fn)))

;; static mixin

(def static-mixin {
  :should-update
  (fn [old-state new-state]
    (not= (::args old-state) (::args new-state)))
})

(defn static-component [render-fn]
  (build-class
    (render-mixin render-fn)
    static-mixin))

;; reactive mixin

(def ^:dynamic *reactions*)

(def reactive-mixin {
  :should-update
  (constantly false) ;; updates through .forceUpdate only
  :render
  (fn [renders state]
    (binding [*reactions* (volatile! #{})]
      (let [comp          (::component state)
            old-reactions (::refs state #{})
            dom           ((first renders) (next renders) state)
            new-reactions @*reactions*
            key           (::id state)]
        (doseq [ref old-reactions]
          (when-not (contains? new-reactions ref)
            (remove-watch ref key)))
        (doseq [ref new-reactions]
          (when-not (contains? old-reactions ref)
            (add-watch ref key
              (fn [_ _ _ _]
                (request-render comp)))))
        (update-state! comp assoc ::refs new-reactions)
        dom)))
  :will-unmount
  (fn [state]
    (let [key (::id state)]
      (doseq [ref (::refs state)]
        (remove-watch ref key)))
    (dissoc state ::refs))
})

(defn reactive-component [render-fn]
  (build-class
    (render-mixin render-fn)
    static-mixin
    reactive-mixin))

(defn react [ref]
  (vswap! *reactions* conj ref)
  @ref)

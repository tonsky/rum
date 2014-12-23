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
  (let [will-mount     (fns :will-mount classes)
        did-mount      (fns :did-mount classes)
        should-update  (fns :should-update classes)
        render         (reverse (fns :render classes))
        will-unmount   (fns :will-unmount classes)
        transfer-state (fns :transfer-state classes)
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
                (let [new-state (aget next-props ":rum/state")
                      init      (assoc @new-state ::component this)
                      old-state @(state this)]
                  (vreset! new-state
                    (reduce #(%2 old-state %1) init transfer-state)))))
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

;; cursors

(deftype LensCursor [parent getter setter]
  Object
  (equiv [this other]
    (-equiv this other))

  IAtom
  
  IEquiv
  (-equiv [this other]
    (identical? this other))

  IDeref
  (-deref [_]
    (getter (-deref parent)))

  IWatchable
  (-add-watch [this key f]
    (add-watch parent (list this key)
      (fn [_ _ oldp newp]
        (let [old (getter oldp)
              new (getter newp)]
          (when (not= old new)
            (f key this old new)))))
    this)
  
  (-remove-watch [this key]
    (remove-watch parent (list this key))
    this)

  IHash
  (-hash [this] (goog/getUid this))

  IReset
  (-reset! [_ new-value]
    (swap! parent setter new-value)
    new-value)

  ISwap
  (-swap! [this f]
    (-reset! this (f (-deref this))))
  (-swap! [this f a]
    (-reset! this (f (-deref this) a)))
  (-swap! [this f a b]
    (-reset! this (f (-deref this) a b)))
  (-swap! [this f a b xs]
    (-reset! this (apply f (-deref this) a b xs)))
  
  IPrintWithWriter
  (-pr-writer [this writer opts]
    (-write writer "#<Cursor: ")
    (pr-writer (-deref this) writer opts)
    (-write writer ">")))

(defn cursor [ref path]
  (let [getter #(get-in % path)
        setter #(assoc-in %1 path %2)]
    (if (instance? LensCursor ref)
      (LensCursor. (.-parent ref)
                   (comp getter (.-getter ref))
                   (fn [where what]
                     (as-> ((.-getter ref) where) focus 
                       (setter focus what)
                       ((.-setter ref) where focus))))
      (LensCursor. ref getter setter))))

;; om-style cursors

(def ^:dynamic *om-refs*)

(defn- deref-args [xs]
  (mapv #(if (satisfies? IDeref %) @% %) xs))

(def om-mixin {
  :transfer-state
  (fn [old new]
    (assoc new ::om-args (::om-args old)))
  :should-update
  (fn [old-state new-state]
    (not= (::om-args old-state) (deref-args (::args new-state))))
  :render
  (fn [renders state]
    (update-state! (::component state) assoc ::om-args (deref-args (::args state)))
    ((first renders) (next renders) state))
})

(defn om-component [render-fn]
  (build-class
    (render-mixin render-fn)
    om-mixin))

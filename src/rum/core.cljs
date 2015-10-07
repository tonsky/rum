(ns rum.core
  (:require-macros rum.core)
  (:require
    [cljsjs.react]
    [sablono.core]))

(let [last-id (volatile! 0)]
  (defn next-id []
    (vswap! last-id inc)))

(defn state [comp]
  (aget (.-state comp) ":rum/state"))

(defn id [comp]
  (:rum/id @(state comp)))

(defn- collect [fn-key classes]
  (->> classes
       (map fn-key)
       (remove nil?)))

(defn- call-all [state fns & args]
  (reduce
    (fn [state fn]
      (apply fn state args))
    state
    fns))

(defn build-class [classes display-name]
  (assert (sequential? classes))
  (let [init                (collect :init classes)                ;; state props -> state
        will-mount          (collect :will-mount classes)          ;; state -> state
        did-mount           (collect :did-mount classes)           ;; state -> state
        transfer-state      (collect :transfer-state classes)      ;; old-state state -> state
        should-update       (collect :should-update classes)       ;; old-state state -> boolean
        will-update         (collect :will-update classes)         ;; state -> state
        render              (first (collect :render classes))      ;; state -> [dom state]
        wrapped-render      (reduce #(%2 %1) render (collect :wrap-render classes)) ;; render-fn -> render-fn
        did-update          (collect :did-update classes)          ;; state -> state
        will-unmount        (collect :will-unmount classes)        ;; state -> state
        props->state        (fn [props]
                              (call-all (aget props ":rum/initial-state") init props))
        child-context       (collect :child-context classes)       ;; state -> child-context
        class-properties    (reduce merge (collect :class-properties classes))] ;; custom properties and methods

    (-> {:displayName display-name
         :getInitialState
         (fn []
           (this-as this
             (let [props (.-props this)
                   state (-> { :rum/react-component this
                               :rum/id (next-id) }            ;; assign id on mount?
                             (merge (props->state props)))]
               #js { ":rum/state" (volatile! state) })))
         :componentWillMount
         (when-not (empty? will-mount)
           (fn []
             (this-as this
               (vswap! (state this) call-all will-mount))))
         :componentDidMount
         (when-not (empty? did-mount)
           (fn []
             (this-as this
               (vswap! (state this) call-all did-mount))))
         :componentWillReceiveProps
         (fn [next-props]
           (this-as this
             (let [old-state @(state this)
                   next-state (-> { :rum/react-component this
                                    :rum/id (:rum/id old-state) }
                                  (merge (props->state next-props)))
                   next-state (reduce #(%2 old-state %1) next-state transfer-state)]
               (.setState this #js {":rum/state" (volatile! next-state)}))))
         :shouldComponentUpdate
         (when-not (empty? should-update)
           (fn [next-props next-state]
             (this-as this
               (let [old-state @(state this)
                     new-state @(aget next-state ":rum/state")]
                 (or (some #(% old-state new-state) should-update) false)))))
         :componentWillUpdate
         (when-not (empty? will-update)
           (fn [_ next-state]
             (this-as this
               (let [new-state (aget next-state ":rum/state")]
                 (vswap! new-state call-all will-update)))))
         :render
         (fn []
           (this-as this
             (let [state (state this)
                   [dom next-state] (wrapped-render @state)]
               (vreset! state next-state)
               dom)))
         :componentDidUpdate
         (when-not (empty? did-update)
           (fn [_ _]
             (this-as this
               (vswap! (state this) call-all did-update))))
         :componentWillUnmount
         (when-not (empty? will-unmount)
           (fn []
             (this-as this
               (vswap! (state this) call-all will-unmount))))
         :getChildContext
         (when-not (empty? child-context)
           (fn []
             (this-as this
               (let [state @(state this)]
                 (clj->js (transduce (map #(% state)) merge {} child-context))))))}
      (merge class-properties)
      (clj->js)
      (js/React.createClass))))


;; render queue

(def schedule
  (or (and (exists? js/window)
           (or js/window.requestAnimationFrame
               js/window.webkitRequestAnimationFrame
               js/window.mozRequestAnimationFrame
               js/window.msRequestAnimationFrame))
    #(js/setTimeout % 16)))

(defn compare-by [keyfn]
  (fn [x y]
    (compare (keyfn x) (keyfn y))))

(def empty-queue (sorted-set-by (compare-by id))) ;; sorted by mount order, top to bottom
(def render-queue (volatile! empty-queue))

(defn render []
  (let [queue @render-queue]
    (vreset! render-queue empty-queue)
    (doseq [comp queue
            :when (.isMounted comp)]
      (.forceUpdate comp))))

(defn request-render [component]
  (when (empty? @render-queue)
    (schedule render))
  (vswap! render-queue conj component))

(defn mount [component node]
  (js/React.render component node))

;; initialization

(defn render->mixin [render-fn]
  { :render (fn [state] [(apply render-fn (:rum/args state)) state]) })

(defn render-state->mixin [render-fn]
  { :render (fn [state] [(apply render-fn state (:rum/args state)) state]) })

(defn render-comp->mixin [render-fn]
  { :render (fn [state] [(apply render-fn (:rum/react-component state) (:rum/args state)) state]) })

(defn args->state [args]
  {:rum/args args})

(defn element [class state & [props]]
  (let [props (or props #js {})]
    (aset props ":rum/initial-state" state)
    (js/React.createElement class props)))

(defn ctor->class [ctor]
  (:rum/class (meta ctor)))

(defn with-key [element key]
  (js/React.cloneElement element #js { "key" key } nil))

(defn with-ref [element ref]
  (js/React.cloneElement element #js { "ref" ref } nil))

;; static mixin

(def static {
  :should-update
  (fn [old-state new-state]
    (not= (:rum/args old-state) (:rum/args new-state)))
})

;; local mixin

(defn local
  "Adds an atom to component’s state that can be used as local state.
   Atom is stored under key `:rum/local`.
   Component will be automatically re-rendered if atom’s value changes"
  [initial & [key]]
  (let [key (or key :rum/local)]
    { :transfer-state
      (fn [old new]
        (assoc new key (old key)))
      :will-mount
      (fn [state]
        (let [local-state (atom initial)
              component   (:rum/react-component state)]
          (add-watch local-state key
            (fn [_ _ _ _]
              (request-render component)))
          (assoc state key local-state))) }))


;; reactive mixin

(def ^:dynamic *reactions*)

(defn- reactive-key [state]
  (str ":rum/reactive-" (:rum/id state)))

(def reactive {
  :transfer-state
  (fn [old new]
    (assoc new :rum/refs (:rum/refs old)))
  :wrap-render
  (fn [render-fn]
    (fn [state]
      (binding [*reactions* (volatile! #{})]
        (let [comp             (:rum/react-component state)
              old-reactions    (:rum/refs state #{})
              [dom next-state] (render-fn state)
              new-reactions    @*reactions*
              key              (reactive-key state)]
          (doseq [ref old-reactions]
            (when-not (contains? new-reactions ref)
              (remove-watch ref key)))
          (doseq [ref new-reactions]
            (when-not (contains? old-reactions ref)
              (add-watch ref key
                (fn [_ _ _ _]
                  (request-render comp)))))
          [dom (assoc next-state :rum/refs new-reactions)]))))
  :will-unmount
  (fn [state]
    (let [key (reactive-key state)]
      (doseq [ref (:rum/refs state)]
        (remove-watch ref key)))
    (dissoc state :rum/refs))
})

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

(defn- deref-args [xs]
  ;; deref is not deep
  (mapv #(if (satisfies? IDeref %) @% %) xs))

(def cursored {
  :transfer-state
  (fn [old new]
    (assoc new :rum/om-args (:rum/om-args old)))
  :should-update
  (fn [old-state new-state]
    (not= (:rum/om-args old-state) (deref-args (:rum/args new-state))))
  :wrap-render
  (fn [render-fn]
    (fn [state]
      (let [[dom next-state] (render-fn state)]
        [dom (assoc next-state :rum/om-args (deref-args (:rum/args state)))])))
})

(defn- cursored-key [state]
  (str ":rum/cursored-" (:rum/id state)))

(def cursored-watch {
  :did-mount
    (fn [state]
      (doseq [arg (:rum/args state)
              :when (satisfies? IWatchable arg)]
        (add-watch arg (cursored-key state)
          (fn [_ _ _ _] (request-render (:rum/react-component state)))))
      state)
  :will-unmount
    (fn [state]
      (doseq [arg (:rum/args state)
              :when (satisfies? IWatchable arg)]
        (remove-watch arg (cursored-key state)))
      state)
})

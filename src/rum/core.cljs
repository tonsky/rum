(ns rum.core
  (:require-macros
    rum.core)
  (:require
    [cljsjs.react]
    [cljsjs.react.dom]
    [sablono.core]
    [rum.cursor :as cursor]
    [rum.util :as util :refer [next-id collect call-all]]))


(defn state [comp]
  (aget (.-state comp) ":rum/state"))


(defn build-stateful-class [classes display-name]
  (assert (sequential? classes))
  (let [init                (collect :init classes)                ;; state props -> state
        will-mount          (collect :will-mount classes)          ;; state -> state
        did-mount           (collect :did-mount classes)           ;; state -> state
        did-remount         (collect :did-remount classes)         ;; old-state state -> state
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
                   state (-> { :rum/react-component this }
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
             (let [old-state  @(state this)
                   state      (merge old-state
                                     (aget next-props ":rum/initial-state"))
                   next-state (reduce #(%2 old-state %1) state did-remount)]
               ;; allocate new volatile so that we can access both old and new states in shouldComponentUpdate
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


(defn build-stateless-class [class display-name]
  (let [render (:render class)
        class  (fn [props]
                 (let [[dom _] (render (aget props ":rum/initial-state"))]
                   dom))]
    (aset class "displayName" display-name)
    class))


(defn build-class [classes display-name]
  (case (count classes)
    1 (build-stateless-class (first classes) display-name)
    (build-stateful-class classes display-name)))


;; render queue

(def schedule
  (or (and (exists? js/window)
           (or js/window.requestAnimationFrame
               js/window.webkitRequestAnimationFrame
               js/window.mozRequestAnimationFrame
               js/window.msRequestAnimationFrame))
    #(js/setTimeout % 16)))

(def empty-queue [])
(def render-queue (volatile! empty-queue))

(defn render-all [queue]
  (doseq [comp queue
          :when (.isMounted comp)]
    (.forceUpdate comp)))

(defn render []
  (let [queue @render-queue]
    (vreset! render-queue empty-queue)
    (js/ReactDOM.unstable_batchedUpdates render-all queue)))

(defn request-render [component]
  (when (empty? @render-queue)
    (schedule render))
  (vswap! render-queue conj component))

(defn mount [component node]
  (js/ReactDOM.render component node)
  nil)

(defn unmount [node]
  (js/ReactDOM.unmountComponentAtNode node))

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

(defn dom-node [state]
  (js/ReactDOM.findDOMNode (:rum/react-component state)))

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
    { :will-mount
      (fn [state]
        (let [local-state (atom initial)
              component   (:rum/react-component state)]
          (add-watch local-state key
            (fn [_ _ _ _]
              (request-render component)))
          (assoc state key local-state))) }))


;; reactive mixin

(def ^:dynamic *reactions*)


(def reactive {
  :init
  (fn [state props]
    (assoc state :rum.reactive/key (random-uuid)))
  :wrap-render
  (fn [render-fn]
    (fn [state]
      (binding [*reactions* (volatile! #{})]
        (let [comp             (:rum/react-component state)
              old-reactions    (:rum.reactive/refs state #{})
              [dom next-state] (render-fn state)
              new-reactions    @*reactions*
              key              (:rum.reactive/key state)]
          (doseq [ref old-reactions]
            (when-not (contains? new-reactions ref)
              (remove-watch ref key)))
          (doseq [ref new-reactions]
            (when-not (contains? old-reactions ref)
              (add-watch ref key
                (fn [_ _ _ _]
                  (request-render comp)))))
          [dom (assoc next-state :rum.reactive/refs new-reactions)]))))
  :will-unmount
  (fn [state]
    (let [key (:rum.reactive/key state)]
      (doseq [ref (:rum.reactive/refs state)]
        (remove-watch ref key)))
    (dissoc state :rum.reactive/refs :rum.reactive/key))
})


(defn react [ref]
  (vswap! *reactions* conj ref)
  @ref)


;; derived-atom

(def derived-atom util/derived-atom)


;; cursors


(defn cursor-in [ref path & {:as options}]
  (if (instance? cursor/Cursor ref)
    (cursor/Cursor. (.-ref ref) (into (.-path ref) path) (:meta options))
    (cursor/Cursor. ref path (:meta options))))


(defn cursor [ref key]
  (cursor-in ref [key]))


;; Om-style mixins

(defn- deref-args [xs]
  ;; deref is not deep
  (mapv #(if (satisfies? IDeref %) @% %) xs))


(def cursored {
  :should-update
  (fn [old-state new-state]
    (not= (:rum/om-args old-state) (deref-args (:rum/args new-state))))
  :wrap-render
  (fn [render-fn]
    (fn [state]
      (let [[dom next-state] (render-fn state)]
        [dom (assoc next-state :rum/om-args (deref-args (:rum/args state)))])))
})


(def cursored-watch {
  :init
    (fn [state props]
      (assoc state :rum.cursored/key (random-uuid)))
  :will-mount
    (fn [state]
      (doseq [arg (:rum/args state)
              :when (satisfies? IWatchable arg)]
        (add-watch arg (:rum.cursored/key state)
          (fn [_ _ _ _] (request-render (:rum/react-component state)))))
      state)
  :will-unmount
    (fn [state]
      (doseq [arg (:rum/args state)
              :when (satisfies? IWatchable arg)]
        (remove-watch arg (:rum.cursored/key state)))
      state)
})

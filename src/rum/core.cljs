(ns rum.core
  (:refer-clojure :exclude [ref])
  (:require-macros rum.core)
  (:require
    [cljsjs.react]
    [cljsjs.react.dom]
    [sablono.core]
    [rum.cursor :as cursor]
    [rum.util :as util :refer [collect collect* call-all]]
    [rum.derived-atom :as derived-atom]))


(defn state [comp]
  (aget (.-state comp) ":rum/state"))


(defn build-class [render mixins display-name]
  (let [init           (collect   :init mixins)             ;; state props -> state
        will-mount     (collect* [:will-mount               ;; state -> state
                                  :before-render] mixins)   ;; state -> state
        render         render                               ;; state -> [dom state]
        wrap-render    (collect   :wrap-render mixins)      ;; render-fn -> render-fn
        wrapped-render (reduce #(%2 %1) render wrap-render)
        did-mount      (collect* [:did-mount                ;; state -> state
                                  :after-render] mixins)    ;; state -> state
        did-remount    (collect   :did-remount mixins)      ;; old-state state -> state
        should-update  (collect   :should-update mixins)    ;; old-state state -> boolean
        will-update    (collect  [:will-update              ;; state -> state
                                  :before-render] mixins)   ;; state -> state
        did-update     (collect* [:did-update               ;; state -> state
                                  :after-render] mixins)    ;; state -> state
        will-unmount   (collect   :will-unmount mixins)     ;; state -> state
        child-context  (collect   :child-context mixins)    ;; state -> child-context
        class-props    (reduce merge (collect :class-properties mixins))] ;; custom properties and methods

    (-> {:displayName display-name
         :getInitialState
         (fn []
           (this-as this
             (let [props (.-props this)
                   state (-> (aget props ":rum/initial-state")
                             (call-all init props)
                             (assoc :rum/react-component this))]
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
      (merge class-props)
      (clj->js)
      (js/React.createClass))))


(defn build-ctor [render mixins display-name]
  (let [class  (build-class render mixins display-name)
        key-fn (first (collect :key-fn mixins))
        ctor   (if (some? key-fn)
                 (fn [& args]
                   (let [props #js { ":rum/initial-state" { :rum/args args }
                                     "key" (apply key-fn args) }]
                     (js/React.createElement class props)))
                 (fn [& args]
                   (let [props #js { ":rum/initial-state" { :rum/args args }}] 
                     (js/React.createElement class props))))]
    (with-meta ctor { :rum/class class })))


(defn build-defc [render-body mixins display-name]
  (if (empty? mixins)
    (let [class (fn [props]
                  (apply render-body (aget props ":rum/args")))
          _     (aset class "displayName" display-name)
          ctor  (fn [& args]
                  (js/React.createElement class #js { ":rum/args" args }))]
      (with-meta ctor { :rum/class class }))
    (let [render (fn [state] [(apply render-body (:rum/args state)) state])]
      (build-ctor render mixins display-name))))


(defn build-defcs [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body state (:rum/args state)) state])]
    (build-ctor render mixins display-name)))


(defn build-defcc [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body (:rum/react-component state) (:rum/args state)) state])] 
    (build-ctor render mixins display-name)))


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

(defn with-key [element key]
  (js/React.cloneElement element #js { "key" key } nil))

(defn with-ref [element ref]
  (js/React.cloneElement element #js { "ref" ref } nil))

(defn dom-node [state]
  (js/ReactDOM.findDOMNode (:rum/react-component state)))

(defn ref [state key]
  (-> state :rum/react-component (aget "refs") (aget (name key))))

(defn ref-node [state key]
  (js/ReactDOM.findDOMNode (ref state (name key))))

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

(def derived-atom derived-atom/derived-atom)


;; cursors

(defn cursor-in [ref path & {:as options}]
  (if (instance? cursor/Cursor ref)
    (cursor/Cursor. (.-ref ref) (into (.-path ref) path) (:meta options))
    (cursor/Cursor. ref path (:meta options))))


(defn cursor [ref key]
  (cursor-in ref [key]))

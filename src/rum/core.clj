(ns rum.core
  (:require
    [sablono.compiler :as s]
    [rum.cursor :as cursor]
    [rum.server-render :as render]
    [rum.util :as util :refer [next-id collect call-all]])
  (:import
    [rum.cursor Cursor]))


(defn- fn-body? [form]
  (and (seq? form)
       (vector? (first form))))


(defn- parse-defc
  ":name  :doc?  <? :mixins* :bodies+
   symbol string <  exprs    fn-body?"
  [xs]
  (when-not (instance? clojure.lang.Symbol (first xs))
    (throw (IllegalArgumentException. "First argument to defc must be a symbol")))
  (loop [res  {}
         xs   xs
         mode nil]
    (let [x    (first xs)
          next (next xs)]
      (cond
        (and (empty? res) (symbol? x))
          (recur {:name x} next nil)
        (fn-body? xs)        (assoc res :bodies (list xs))
        (every? fn-body? xs) (assoc res :bodies xs)
        (string? x)          (recur (assoc res :doc x) next nil)
        (= '< x)             (recur res next :mixins)
        (= mode :mixins)
          (recur (update-in res [:mixins] (fnil conj []) x) next :mixins)
        :else
          (throw (IllegalArgumentException. (str "Syntax error at " xs)))))))


(defn- compile-body [[argvec & body]]
  (list argvec (s/compile-html `(do ~@body))))


(defn- -defc [render-ctor cljs? body]
  (let [{:keys [name doc mixins bodies]} (parse-defc body)
        render-fn (if cljs?
                    (map compile-body bodies)
                    bodies)
        arglists  (if (= render-ctor 'rum.core/render->mixin)
                    (map (fn [[arglist & _body]] arglist) bodies)
                    (map (fn [[[_ & arglist] & _body]] (vec arglist)) bodies))]
    `(def ~(vary-meta name update :arglists #(or % `(quote ~arglists)))
       ~@(if doc [doc] [])
       (let [render-mixin# (~render-ctor (fn ~@render-fn))
             class#        (rum.core/build-class (concat [render-mixin#] ~mixins) ~(str name))
             ctor#         (fn [& args#]
                             (let [state# (args->state args#)]
                               (rum.core/element class# state# nil)))]
         (with-meta ctor# {:rum/class class#})))))


(defmacro defc
  "Defc does couple of things:
   
     1. Wraps body into sablono/compile-html
     2. Generates render function from that
     3. Takes render function and mixins, builds React class from them
     4. Using that class, generates constructor fn [params]->ReactElement
     5. Defines top-level var with provided name and assigns ctor to it
  
   Usage:
  
       (defc name doc-string? [< mixins+]? [params*] render-body+)"
  [& body]
  (-defc 'rum.core/render->mixin (boolean (:ns &env)) body))


(defmacro defcs
  "Same as defc, but render will take additional first argument: state
  
   Usage:

        (defcs name doc-string? [< mixins+]? [state params*] render-body+)"
  [& body]
  (-defc 'rum.core/render-state->mixin (:ns &env) body))


(defmacro defcc
  "Same as defc, but render will take additional first argument: react component
  
   Usage:

        (defcc name doc-string? [< mixins+]? [comp params*] render-body+)"
  [& body]
  (-defc 'rum.core/render-comp->mixin (:ns &env) body))


(defmacro with-props
  "DEPRECATED. Use rum.core/with-key and rum.core/with-ref functions
  
   Calling function returned by defc will get you component. To specify
   special React properties, create component using with-props:
   
       (rum.core/with-props <ctor> <arg1> <arg2> :rum/key <key>)
  
   Special properties goes at the end of arguments list and should be namespaced.
   For now only :rum/key and :rum/ref are supported"
  [ctor & args]
  (let [props {:rum/key "key"
               :rum/ref "ref"}
        as (take-while #(not (props %)) args)
        ps (->> (drop-while #(not (props %)) args)
                (partition 2)
                (mapcat (fn [[k v]] [(props k) v])))]
    `(rum.core/element (ctor->class ~ctor) (args->state [~@as]) (cljs.core/js-obj ~@ps))))


(def derived-atom util/derived-atom)


;;; Server-side rendering support

(def render-html render/render-html)
(def render-static-markup render/render-static-markup)


(defn build-class [classes display-name]
  (assert (sequential? classes))
  (let [init             (collect :init classes)                ;; state props -> state
        will-mount       (collect :will-mount classes)          ;; state -> state
        render           (first (collect :render classes))      ;; state -> [dom state]
        wrapped-render   (reduce #(%2 %1) render (collect :wrap-render classes)) ;; render-fn -> render-fn
        props->state     (fn [props]
                           (call-all (:rum/initial-state props) init props))]

    (fn [props]
      (let [state       (-> {:rum/id (next-id)}
                            (merge (props->state props))
                            (call-all will-mount))
            [dom state] (wrapped-render state)]
        (or dom [:rum/nothing])))))


(defn args->state [args]
  {:rum/args args})


(defn element [class state & [props]]
  (class (assoc props :rum/initial-state state)))


(defn render->mixin [render-fn]
  { :render (fn [state] [(apply render-fn (:rum/args state)) state]) })


(defn render-state->mixin [render-fn]
  { :render (fn [state] [(apply render-fn state (:rum/args state)) state]) })


(defn render-comp->mixin [render-fn]
  { :render (fn [state] [(apply render-fn (:rum/react-component state) (:rum/args state)) state]) })


(defn with-key [element key]
  (cond
    (render/nothing? element)
    element
    
    (map? (get element 1))
    (assoc-in element [1 :key] key)

    :else
    (into [(first element) {:key key}] (next element))))


(defn with-ref [element ref]
  element)


;; mixins


(def static {})


(defn local
  ([initial] (local initial :rum/local))
  ([initial key]
    {:will-mount (fn [state]
                   (assoc state key (atom initial)))}))


(def reactive {})
(def react deref)


(defn cursor-in ^rum.cursor.Cursor [ref path & { :as options }]
  (if (instance? Cursor ref)
    (cursor/Cursor. (.-ref ^Cursor ref) (into (.-path ^Cursor ref) path) (:meta options) (volatile! {}))
    (cursor/Cursor. ref path (:meta options) (volatile! {}))))


(defn cursor ^rum.cursor.Cursor [ref key & options]
  (apply cursor-in ref [key] options))


(def cursored {})
(def cursored-watch {})


;; method parity with CLJS version so you can avoid conditional directive
;; in e.g. did-mount/will-unmount mixin bodies

(defn dom-node [s]
  (throw (UnsupportedOperationException. "dom-node is only avaliable from ClojureScript")))


(defn mount [c n]
  (throw (UnsupportedOperationException. "mount is only avaliable from ClojureScript")))


(defn unmount [c]
  (throw (UnsupportedOperationException. "unmount is only avaliable from ClojureScript")))


(defn request-render [c]
  (throw (UnsupportedOperationException. "request-render is only avaliable from ClojureScript")))


(defn state [c]
  (throw (UnsupportedOperationException. "state is only avaliable from ClojureScript")))


(defn id [c]
  (throw (UnsupportedOperationException. "id is only avaliable from ClojureScript")))

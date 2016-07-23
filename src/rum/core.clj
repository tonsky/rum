(ns rum.core
  (:refer-clojure :exclude [ref])
  (:require
    [sablono.compiler :as s]
    [rum.cursor :as cursor]
    [rum.server-render :as render]
    [rum.util :as util :refer [collect collect* call-all]]
    [rum.derived-atom :as derived-atom])
  (:import
    [rum.cursor Cursor]))


(defn- fn-body? [form]
  (when (and (seq? form)
             (vector? (first form)))
    (if (= '< (second form))
      (throw (IllegalArgumentException. "Mixins must be given before argument list"))
      true)))


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


(defn- compile-body [[argvec conditions & body]]
  (if (and (map? conditions) (seq body))
    (list argvec conditions (s/compile-html `(do ~@body)))
    (list argvec (s/compile-html `(do ~@(cons conditions body))))))


(defn- -defc [builder cljs? body]
  (let [{:keys [name doc mixins bodies]} (parse-defc body)
        render-body (if cljs?
                      (map compile-body bodies)
                      bodies)
        arglists  (if (= builder 'rum.core/build-defc)
                    (map (fn [[arglist & _body]] arglist) bodies)
                    (map (fn [[[_ & arglist] & _body]] (vec arglist)) bodies))]
    `(def ~(vary-meta name update :arglists #(or % `(quote ~arglists)))
       ~@(if doc [doc] [])
       (~builder (fn ~@render-body) ~mixins ~(str name)))))


(defmacro defc
  "Defc does couple of things:
   
     1. Wraps body into sablono/compile-html
     2. Generates render function from that
     3. Takes render function and mixins, builds React class from them
     4. Using that class, generates constructor fn [args]->ReactElement
     5. Defines top-level var with provided name and assigns ctor to it
  
  (rum/defc label [t]
    [:div t])
  
  ;; creates React class
  ;; defines ctor fn (defn label [t] ...) => element
  
  (label \"text\") ;; => returns React element built with label class
  
   Usage:
  
       (defc name doc-string? [< mixins+]? [params*] render-body+)"
  [& body]
  (-defc 'rum.core/build-defc (boolean (:ns &env)) body))


(defmacro defcs
  "Same as defc, but render will take additional first argument: state
  
   Usage:

        (defcs name doc-string? [< mixins+]? [state params*] render-body+)"
  [& body]
  (-defc 'rum.core/build-defcs (boolean (:ns &env)) body))


(defmacro defcc
  "Same as defc, but render will take additional first argument: react component
  
   Usage:

        (defcc name doc-string? [< mixins+]? [comp params*] render-body+)"
  [& body]
  (-defc 'rum.core/build-defcc (boolean (:ns &env)) body))


(defn- build-ctor [render mixins display-name]
  (let [init           (collect :init mixins)                ;; state props -> state
        will-mount     (collect* [:will-mount                ;; state -> state
                                  :before-render] mixins)    ;; state -> state
        render         render                                ;; state -> [dom state]
        wrapped-render (reduce #(%2 %1) render (collect :wrap-render mixins))] ;; render-fn -> render-fn
    (fn [& args]
      (let [props   nil
            state   (-> { :rum/args args }
                        (call-all init props)
                        (call-all will-mount))
            [dom _] (wrapped-render state)]
        (or dom [:rum/nothing])))))


(defn build-defc [render-body mixins display-name]
  (if (empty? mixins)
    (fn [& args] (or (apply render-body args) [:rum/nothing]))
    (let [render (fn [state] [(apply render-body (:rum/args state)) state])]
      (build-ctor render mixins display-name))))


(defn build-defcs [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body state (:rum/args state)) state])]
    (build-ctor render mixins display-name)))


(defn build-defcc [render-body mixins display-name]
  (let [render (fn [state] [(apply render-body (:rum/react-component state) (:rum/args state)) state])]
    (build-ctor render mixins display-name)))


;; rum.core APIs


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


(defn cursor-in
  "Given atom with deep nested value and path inside it, creates an atom-like structure
   that can be used separately from main atom, but will sync changes both ways:
  
     (def db (atom { :users { \"Ivan\" { :age 30 }}}))
     (def ivan (rum/cursor db [:users \"Ivan\"]))
     \\@ivan ;; => { :age 30 }
     (swap! ivan update :age inc) ;; => { :age 31 }
     \\@db ;; => { :users { \"Ivan\" { :age 31 }}}
     (swap! db update-in [:users \"Ivan\" :age] inc) ;; => { :users { \"Ivan\" { :age 32 }}}
     \\@ivan ;; => { :age 32 }
  
  Returned value supports deref, swap!, reset!, watches and metadata.
  The only supported option is `:meta`"
  ^rum.cursor.Cursor [ref path & { :as options }]
  (if (instance? Cursor ref)
    (cursor/Cursor. (.-ref ^Cursor ref) (into (.-path ^Cursor ref) path) (:meta options) (volatile! {}))
    (cursor/Cursor. ref path (:meta options) (volatile! {}))))


(defn cursor
  "Same as `rum.core/cursor-in` but accepts single key instead of path vector"
  ^rum.cursor.Cursor [ref key & options]
  (apply cursor-in ref [key] options))

(def ^{:style/indent 2} derived-atom
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
  derived-atom/derived-atom)


;;; Server-side rendering


(def render-html
  "Main server-side rendering method. Given component, returns HTML string with
   static markup of that component. Serve that string to the browser and
   `rum.core/mount` same Rum component over it. React will be able to reuse already
   existing DOM and will initialize much faster"
  render/render-html)

(def render-static-markup
  "Same as `rum.core/render-html` but returned string has nothing React-specific.
   This allows Rum to be used as traditional server-side template engine"
  render/render-static-markup)


;; method parity with CLJS version so you can avoid conditional directive
;; in e.g. did-mount/will-unmount mixin bodies


(defn state [c]
  (throw (UnsupportedOperationException. "state is only available from ClojureScript")))


(defn dom-node [s]
  (throw (UnsupportedOperationException. "dom-node is only available from ClojureScript")))


(defn ref [s k]
  (throw (UnsupportedOperationException. "ref is only available from ClojureScript")))


(defn ref-node [s k]
  (throw (UnsupportedOperationException. "ref is only available from ClojureScript")))


(defn mount [c n]
  (throw (UnsupportedOperationException. "mount is only available from ClojureScript")))


(defn unmount [c]
  (throw (UnsupportedOperationException. "unmount is only available from ClojureScript")))


(defn request-render [c]
  (throw (UnsupportedOperationException. "request-render is only available from ClojureScript")))

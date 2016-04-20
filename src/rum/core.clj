(ns rum.core
  (:require
   [sablono.compiler :as s]
   [rum.server :as server]
   [rum.server-render :as render]))

(defn- fn-body? [form]
  (and (seq? form)
       (vector? (first form))))

(defn- parse-defc [xs]
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
    `(def ~(with-meta name `{:arglists '~arglists}) ~(or doc "")
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

;;; Server-side rendering support

(def render-html render/render-html)

(def build-class server/build-class)
(def args->state server/args->state)
(def element server/element)
(def render->mixin server/render->mixin)
(def render-state->mixin server/render-state->mixin)
(def render-comp->mixin server/render-comp->mixin)
(def with-key server/with-key)
(def with-ref server/with-ref)

;; included mixins
(def static {})

(defn local [initial & [key]]
  (let [key (or key :rum/local)]
    {:will-mount (fn [state]
                   (assoc state key (atom initial)))}))

(def reactive {})
(def react deref)

(defn cursor [ref path]
  (atom (get-in @ref path)))
(def cursored {})
(def cursored-watch {})

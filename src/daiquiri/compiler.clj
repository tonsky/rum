(ns daiquiri.compiler
  (:refer-clojure :exclude [requiring-resolve])
  (:require [daiquiri.normalize :as normalize]
            [daiquiri.util :refer :all]
            [clojure.set :as set]))

(def warn-on-interpretation (atom false))

(defn- requiring-resolve [sym]
  (or (resolve sym)
      (do (require (-> sym namespace symbol))
          (resolve sym))))

(defn maybe-warn-on-interpret
  ([env expr]
   (maybe-warn-on-interpret env expr nil))
  ([env expr tag]
   (when @warn-on-interpretation
     (binding [*out* *err*]
       (let [column (:column env)
             line (:line env)]
         (require 'cljs.analyzer)
         (println (str "WARNING: interpreting by default at " (some-> (requiring-resolve 'cljs.analyzer/*cljs-file*) deref) ":" line ":" column))
         (prn expr)
         (when tag
           (println "Inferred tag was:" tag)))))))

(def ^:private primitive-types
  "The set of primitive types that can be handled by React."
  #{'js 'clj-nil 'js/React.Element
    'number 'string 'boolean 'symbol
    'array 'function})

(defn- primitive-type?
  "Return true if `tag` is a primitive type that can be handled by
  React, otherwise false. "
  [tags]
  (and (not (empty? tags))
       (set/subset? tags primitive-types)))

(defn- normalize-tags [tags]
  (if (set? tags) tags (set [tags])))

(defn infer-tag
  "Infer the tag of `form` using `env`."
  [env form]
  (when env
    (let [*analyzer-warnings* (requiring-resolve 'cljs.analyzer/*cljs-warnings*)
          ;; We would want to use cljs.analyzer/no-warn here but we can't since
          ;; it's a macro and won't work with requiring-resolve
          e (with-bindings* {*analyzer-warnings* (zipmap (keys @*analyzer-warnings*) (repeat false))}
              (fn []
                ((requiring-resolve 'cljs.analyzer/analyze) env form)))
          ;; Roman. Propagating Rum's component return tag
          ;; via :rum/tag meta field, because a component
          ;; is generated as a `def` instead of `defn`
          rum-tag (when (= :invoke (:op e))
                    (-> e :fn :info :meta :rum/tag second))]
      (if rum-tag
        (normalize-tags rum-tag)
        (when-let [tags ((requiring-resolve 'cljs.analyzer/infer-tag) env e)]
          (normalize-tags tags))))))

(declare to-js to-js-map)

(defmulti compile-attr (fn [name value] name))

(defmethod compile-attr :class [_ value]
  (cond
    (or (nil? value)
        (keyword? value)
        (string? value))
    value
    (and (or (sequential? value)
             (set? value))
         (every? string? value))
    (join-classes value)
    :else `(daiquiri.util/join-classes ~value)))

(defmethod compile-attr :style [_ value]
  (let [value (camel-case-keys value)]
    (if (map? value)
      (to-js-map value)
      `(daiquiri.interpreter/element-attributes ~value))))

(defmethod compile-attr :default [_ value]
  (to-js value))

(defn wrap-event-handlers
  "Wrapping on-change handler to work around async rendering queue
  that causes jumping caret and lost characters in input fields"
  [attrs]
  (cond-> attrs
          (:on-change attrs) (assoc :on-change `(rum.core/mark-sync-update ~(:on-change attrs)))))

(defn compile-attrs
  "Compile a HTML attribute map."
  [attrs]
  (when (seq attrs)
    (->> (seq attrs)
         (reduce (fn [attrs [name value]]
                   (assoc attrs name (compile-attr name value)))
                 nil)
         wrap-event-handlers
         html-to-dom-attrs
         to-js-map)))

(defn compile-merge-attrs [attrs-1 attrs-2]
  (let [empty-attrs? #(or (nil? %1) (and (map? %1) (empty? %1)))]
    (cond
      (and (empty-attrs? attrs-1)
           (empty-attrs? attrs-2))
      nil
      (empty-attrs? attrs-1)
      `(daiquiri.interpreter/element-attributes ~attrs-2)
      (empty-attrs? attrs-2)
      `(daiquiri.interpreter/element-attributes ~attrs-1)
      (and (map? attrs-1)
           (map? attrs-2))
      (normalize/merge-with-class attrs-1 attrs-2)
      :else `(daiquiri.interpreter/element-attributes
              (daiquiri.normalize/merge-with-class ~attrs-1 ~attrs-2)))))

(defn- compile-tag
  "Replace fragment syntax (`:*` or `:<>`) by 'React.Fragment, otherwise the
  name of the tag"
  [tag]
  (if (fragment-tag? tag)
    'daiquiri.core/fragment
    (name tag)))

(declare compile-react)

(defn compile-react-element
  "Render an element vector as a HTML element."
  [element env]
  (let [[tag attrs content] (normalize/element element)]
    `(daiquiri.core/create-element
      ~(compile-tag tag)
      ~(when (seq attrs)
         (compile-attrs attrs))
      ~(when (seq content)
         `(cljs.core/array ~@(compile-react content env))))))

(defn- unevaluated?
  "True if the expression has not been evaluated."
  [expr]
  (or (symbol? expr)
      (and (seq? expr)
           (not= (first expr) `quote))))

(defmacro interpret-maybe
  "Macro that wraps `expr` with a call to
  `daiquiri.interpreter/interpret` if the inferred return type is not a
  primitive React type."
  [expr]
  (let [tag (infer-tag &env expr)]
    (if (primitive-type? tag)
      expr
      (do
        (maybe-warn-on-interpret &env expr tag)
        `(daiquiri.interpreter/interpret ~expr)))))

(defn- form-name
  "Get the name of the supplied form."
  [form]
  (if (and (seq? form) (symbol? (first form)))
    (name (first form))))

(declare compile-html)

(defmulti compile-form
  "Pre-compile certain standard forms, where possible."
  {:private true}
  (fn [form env] (form-name form)))

(defmethod compile-form "case"
  [[_ v & cases] env]
  `(case ~v
     ~@(doall (mapcat
               (fn [[test hiccup]]
                 (if hiccup
                   [test (compile-html hiccup env)]
                   [(compile-html test env)]))
               (partition-all 2 cases)))))

(defmethod compile-form "cond"
  [[_ & clauses] env]
  `(cond ~@(mapcat
            (fn [[check expr]] [check (compile-html expr env)])
            (partition 2 clauses))))

(defmethod compile-form "condp"
  [[_ f v & cases] env]
  `(condp ~f ~v
     ~@(doall (mapcat
               (fn [[test hiccup]]
                 (if hiccup
                   [test (compile-html hiccup env)]
                   [(compile-html test env)]))
               (partition-all 2 cases)))))

(defmethod compile-form "do"
  [[_ & forms] env]
  `(do ~@(butlast forms) ~(compile-html (last forms) env)))

(defmethod compile-form "let"
  [[_ bindings & body] env]
  `(let ~bindings ~@(butlast body) ~(compile-html (last body) env)))

(defmethod compile-form "let*"
  [[_ bindings & body] env]
  `(let* ~bindings ~@(butlast body) ~(compile-html (last body) env)))

(defmethod compile-form "letfn*"
  [[_ bindings & body] env]
  `(letfn* ~bindings ~@(butlast body) ~(compile-html (last body) env)))

(defmethod compile-form "for"
  [[_ bindings body] env]
  `(~'into-array (for ~bindings ~(compile-html body env))))

(defmethod compile-form "if"
  [[_ condition & body] env]
  `(if ~condition ~@(for [x body] (compile-html x env))))

(defmethod compile-form "if-not"
  [[_ bindings & body] env]
  `(if-not ~bindings ~@(doall (for [x body] (compile-html x env)))))

(defmethod compile-form "if-some"
  [[_ bindings & body] env]
  `(if-some ~bindings ~@(doall (for [x body] (compile-html x env)))))

(defmethod compile-form "when"
  [[_ bindings & body] env]
  `(when ~bindings ~@(doall (for [x body] (compile-html x env)))))

(defmethod compile-form "when-not"
  [[_ bindings & body] env]
  `(when-not ~bindings ~@(doall (for [x body] (compile-html x env)))))

(defmethod compile-form "when-some"
  [[_ bindings & body] env]
  `(when-some ~bindings ~@(butlast body) ~(compile-html (last body) env)))

(defmethod compile-form :default
  [expr env]
  (if (:inline (meta expr))
    expr `(interpret-maybe ~expr)))

(defn- not-hint?
  "True if x is not hinted to be the supplied type."
  [x type]
  (if-let [hint (-> x meta :tag)]
    (not (isa? (eval hint) type))))

(defn- hint?
  "True if x is hinted to be the supplied type."
  [x type]
  (if-let [hint (-> x meta :tag)]
    (isa? (eval hint) type)))

(defn- literal?
  "True if x is a literal value that can be rendered as-is."
  [x]
  (and (not (unevaluated? x))
       (or (not (or (vector? x) (map? x)))
           (every? literal? x))))

(defn- not-implicit-map?
  "True if we can infer that x is not a map."
  [x]
  (or (= (form-name x) "for")
      (not (unevaluated? x))
      (not-hint? x java.util.Map)))

(defn- attrs-hint?
  "True if x has :attrs metadata. Treat x as a implicit map"
  [x]
  (-> x meta :attrs))

(defn- inline-hint?
  "True if x has :inline metadata. Treat x as a implicit map"
  [x]
  (-> x meta :inline))

(defn- element-compile-strategy
  "Returns the compilation strategy to use for a given element."
  [[tag attrs & content :as element] env]
  (cond
    ;; e.g. [:> ...]
    (= tag :>)
    ::react-interop

    ;; e.g. [:span "foo"]
    (every? literal? element)
    ::all-literal

    ;; e.g. [:span {} x]
    (and (literal? tag) (map? attrs))
    ::literal-tag-and-attributes

    ;; e.g. [:span ^String x]
    (and (literal? tag) (not-implicit-map? attrs))
    ::literal-tag-and-no-attributes

    ;; e.g. [:span ^:attrs y] or [:span (attrs)], return type of `attrs` is a map
    (and (literal? tag)
         (or (= '#{cljs.core/IMap} (infer-tag env attrs))
             (attrs-hint? attrs)))
    ::literal-tag-and-hinted-attributes

    ;; e.g. [:span ^:inline (y)]
    (and (literal? tag)
         (or (primitive-type? (infer-tag env attrs))
             (inline-hint? attrs)))
    ::literal-tag-and-inline-content

    ;; ; e.g. [:span x]
    (literal? tag)
    ::literal-tag

    ;; e.g. [x]
    :else
    ::default))

(declare compile-html)

(defmulti compile-element
  "Returns an unevaluated form that will render the supplied vector as a HTML
          element."
  {:private true}
  element-compile-strategy)

(defmethod compile-element ::react-interop
  [element env]
  `(rum.core/adapt-class ~@(rest element)))

(defmethod compile-element ::all-literal
  [element env]
  (compile-react-element (eval element) env))

(defmethod compile-element ::literal-tag-and-attributes
  [[tag attrs & content] env]
  (let [[tag attrs _] (normalize/element [tag attrs])]
    `(daiquiri.core/create-element
      ~(compile-tag tag)
      ~(compile-attrs attrs)
      (cljs.core/array ~@(map #(compile-html % env) content)))))

(defmethod compile-element ::literal-tag-and-no-attributes
  [[tag & content] env]
  (compile-element (apply vector tag {} content) env))

(defmethod compile-element ::literal-tag-and-inline-content
  [[tag & content] env]
  (compile-element (apply vector tag {} content) env))

(defmethod compile-element ::literal-tag-and-hinted-attributes
  [[tag attrs & content] env]
  (let [[tag tag-attrs _] (normalize/element [tag])
        attrs-sym (gensym "attrs")]
    `(let [~attrs-sym ~attrs]
       (daiquiri.core/create-element
        ~(compile-tag tag)
        ~(compile-merge-attrs tag-attrs attrs-sym)
        ~(when-not (empty? content)
           `(cljs.core/array ~@(mapv #(compile-html % env) content)))))))

(defmethod compile-element ::literal-tag
  [[tag attrs & content] env]
  (let [[tag tag-attrs _] (normalize/element [tag])
        attrs-sym (gensym "attrs")]
    `(let [~attrs-sym ~attrs]
       (daiquiri.core/create-element
        ~(compile-tag tag)
        (if (map? ~attrs-sym)
          ~(compile-merge-attrs tag-attrs attrs-sym)
          ~(compile-attrs tag-attrs))
        (if (map? ~attrs-sym)
          ~(when-not (empty? content)
             `(cljs.core/array ~@(mapv #(compile-html % env) content)))
          ~(when attrs
             `(cljs.core/array ~@(mapv #(compile-html % env) (cons attrs-sym content)))))))))

(defmethod compile-element :default
  [element env]
  (maybe-warn-on-interpret env element)
  `(daiquiri.interpreter/interpret
    [~(first element)
     ~@(for [x (rest element)]
         (if (vector? x)
           (compile-element x env)
           x))]))

(defn compile-html
  "Pre-compile data structures into HTML where possible."
  ([content]
   (compile-html content nil))
  ([content env]
   (cond
     (vector? content) (compile-element content env)
     (literal? content) content
     (hint? content String) content
     (hint? content Number) content
     :else (compile-form content env))))

(defn compile-react [v env]
  (cond
    (vector? v) (if (element? v)
                  (compile-react-element v env)
                  (compile-react (seq v) env))
    (seq? v) (map #(compile-react % env) v)
    :else v))

(defn- js-obj [m]
  (let [key-strs (mapv to-js (keys m))
        kvs-str (->> (mapv #(-> (str \' % "':~{}")) key-strs)
                     (interpose ",")
                     (apply str))]
    (vary-meta
     (list* 'js* (str "{" kvs-str "}") (mapv to-js (vals m)))
     assoc :tag 'object)))

(defn- to-js-map
  "Convert a map into a JavaScript object."
  [m]
  (if (every? literal? (keys m))
    (js-obj m)
    `(daiquiri.interpreter/element-attributes ~m)))

(defn- to-js-array
  "Convert a vector into a JavaScript array."
  [x]
  (apply list 'cljs.core/array (mapv to-js x)))

(defn to-js [x]
  (cond
    (keyword? x) (name x)
    (map? x) (to-js-map x)
    (vector? x) (to-js-array x)
    :else x))

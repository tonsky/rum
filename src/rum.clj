(ns rum
  (:require
    [sablono.compiler :as s]))

(defn- parse-defc [xs]
  (loop [res  {}
         xs   xs
         mode nil]
    (let [x    (first xs)
          next (next xs)]
      (cond
        (and (empty? res) (symbol? x))
          (recur {:name x} next nil)
        (vector? x) (assoc res :argvec x
                               :render next)
        (string? x) (recur (assoc res :doc x) next nil)
        (= '< x)    (recur res next :mixins)
        (= mode :mixins)
          (recur (update-in res [:mixins] (fnil conj []) x) next :mixins)
        :else
          (throw (IllegalArgumentException. (str "Syntax error at " xs)))))))

(defn- -defc [render-ctor body]
  (let [{:keys [name doc mixins argvec render]} (parse-defc body)]
   `(let [render-fn#    (fn ~argvec ~(s/compile-html `(do ~@render)))
          render-mixin# (~render-ctor render-fn#)
          class#        (rum/build-class (concat [render-mixin#] ~mixins) ~(str name))
          ctor#         (fn ~argvec
                          (let [state# (args->state ~argvec)]
                            (rum/element class# state# nil)))]
      (def ~name ~doc (with-meta ctor# {::class class#})))))

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
  (-defc 'rum/render->mixin body))

(defmacro defcs
  "Same as defc, but render will take additional first argument: state
  
   Usage:

        (defcs name doc-string? [< mixins+]? [state params*] render-body+)"
  [& body]
  (-defc 'rum/render-state->mixin body))

(defmacro with-props
  "Calling function returned by defc will get you component. To specify
   special React properties, create component using with-props:
   
       (rum/with-props <ctor> <arg1> <arg2> :rum/key <key>)
  
   Special properties goes at the end of arguments list and should be namespaced.
   For now only :rum/key and :rum/ref are supported"
  [ctor & args]
  (let [props {::key "key"
               ::ref "ref"}
        as (take-while #(not (props %)) args)
        ps (->> (drop-while #(not (props %)) args)
                (partition 2)
                (mapcat (fn [[k v]] [(props k) v])))]
    `(rum/element (ctor->class ~ctor) (args->state [~@as]) (cljs.core/js-obj ~@ps))))


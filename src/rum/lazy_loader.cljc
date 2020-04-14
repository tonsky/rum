(ns rum.lazy-loader
  #?(:cljs (:require-macros [rum.lazy-loader]))
  #?(:clj (:require [clojure.spec.alpha :as s]
                    [cljs.core.specs.alpha])
     :cljs (:require [cljs.loader]
                     [cljsjs.react])))

#?(:clj
    (s/def :lazy/libspec
      (s/and
        seq?
        (s/cat
           :quote #{'quote}
           :libspec (s/spec
                     (s/cat
                          :lib simple-symbol?
                          :marker #{:refer}
                          :refer :cljs.core.specs.alpha/refer))))))

#?(:cljs
   (def react-lazy (.-lazy js/React)))

#?(:cljs
   (def load! cljs.loader/load))

#?(:clj
   (s/fdef require-lazy
           :args (s/cat :form :lazy/libspec)))

#?(:clj
   (defmacro require-lazy
     "require-like macro, returns lazy-loaded React components.
     (require-lazy '[my.ns.components :refer [c1 c2]])"
     [form]
     (if-not (:ns &env)
       `(clojure.core/require ~form)
       (let [m (s/conform :lazy/libspec form)]
         (when (not= m :clojure.spec.alpha/invalid)
           (let [{:keys [lib refer]} (:libspec m)
                 module (->> (str lib)
                             (re-find #"\.([a-z0-9-]+)")
                             second
                             keyword)]
             `(do
                ~@(for [sym refer]
                    (let [qualified-sym (symbol (str lib "/" sym))
                          on-load `(fn [ok# fail#]
                                     (load! ~module (fn []
                                                      (ok# (cljs.core/js-obj "default" #(apply (deref (cljs.core/resolve '~qualified-sym)) (aget % ":rum/args")))))))]
                      `(let [lazy# (react-lazy (fn [] (~'js/Promise. ~on-load)))]
                         (defn ~sym [& args#]
                           (.createElement js/React lazy# (cljs.core/js-obj ":rum/args" args#)))))))))))))

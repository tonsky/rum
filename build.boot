(task-options!
  pom { :project     'rum
        :version     "0.3.0"
        :description "ClojureScript wrapper for React"
        :url         "https://github.com/tonsky/rum"
        :scm         {:url "https://github.com/tonsky/rum"}
        :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"} })

(set-env!
  :source-paths   #{"src"}
  :resource-paths #{"src"}
  :dependencies '[
    [org.clojure/clojure       "1.7.0" :scope "provided"]
    [org.clojure/clojurescript "1.7.48"  :scope "provided"]
    [cljsjs/react              "0.13.3-1"]
    [sablono                   "0.3.6"]

    [adzerk/boot-cljs   "1.7.48-2" :scope "test"]
    [adzerk/boot-reload "0.3.1"    :scope "test"]
    [tonsky/boot-anybar "0.1.0"    :scope "test"]
    [pandeiro/boot-http "0.6.3"    :scope "test"]
    [adzerk/bootlaces   "0.1.11"   :scope "test"]
])

(require
  '[adzerk.boot-cljs   :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[tonsky.boot-anybar :refer [anybar]]
  '[pandeiro.boot-http :refer [serve]]
  '[adzerk.bootlaces   :refer [push-release]]
)

(def compiler-opts
  {:warnings {:single-segment-namespace false}})

(deftask serving []
  (merge-env! :source-paths #{"examples"})
  (comp (serve :dir ".")
        (watch)
        (anybar)
        (cljs)))

(deftask none-opts []
  (task-options!
   cljs {:optimizations    :none
         :source-map       true
         :compiler-options compiler-opts})
  identity)

(deftask none []
  (comp (none-opts)
        (serving)
        (reload)))

(deftask advanced-opts []
  (task-options!
   cljs {:optimizations    :advanced
         :compiler-options (merge compiler-opts
                                  {:closure-defines {:goog.DEBUG false}
                                   :elide-asserts   true})})
  identity)

(deftask advanced []
  (comp (advanced-opts)
        (serving)))

(deftask clojars []
  (comp (pom) (jar) (push-release)))

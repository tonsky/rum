(defproject rum "0.2.2"
  :description "ClojureScript wrapper for React"
  :url "https://github.com/tonsky/rum"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src"]
  :dependencies [
    [org.clojure/clojure "1.6.0" :scope "provided"]
    [org.clojure/clojurescript "0.0-2758" :scope "provided"]
    [cljsjs/react "0.12.2-5"]
    [sablono "0.3.1"]
  ]
  
  :plugins [[lein-cljsbuild "1.0.4" :exclusions [org.clojure/clojure]]]
  :clean-targets ^{:protect false} ["target" "web/target-cljs" "web/rum.js"]

  :cljsbuild { :builds [
    { :id "none"
      :source-paths ["src" "examples"]
      :compiler {
        :main          examples
        :output-to     "web/rum.js"
        :output-dir    "web/target-cljs"
        :optimizations :none
        :source-map    true
    }}
    { :id "advanced"
      :source-paths ["src" "examples"]
      :compiler {
        :output-to     "web/rum.js"
        :optimizations :advanced
        :elide-asserts true
        :pretty-print  false
    }}
  ]}
)

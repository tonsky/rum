(defproject rum "0.2.0"
  :description "ClojureScript wrapper for React"
  :url "https://github.com/tonsky/rum"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src"]
  :dependencies [
    [org.clojure/clojure "1.6.0" :scope "provided"]
    [org.clojure/clojurescript "0.0-2665" :scope "provided"]
    [com.facebook/react "0.12.2.1"]
    [sablono "0.2.22"]
  ]
  
  :plugins [[lein-cljsbuild "1.0.4" :exclusions [org.clojure/clojure]]]
  :clean-targets ^{:protect false} ["target" "web/target-cljs" "web/rum.js" "web/rum.min.js"]

  :cljsbuild { :builds [
    { :id :dev
      :source-paths ["src" "examples"]
      :compiler {
        :output-to     "web/rum.js"
        :output-dir    "web/target-cljs"
        :optimizations :none
        :source-map    true
    }}
    { :id :release
      :source-paths ["src" "examples"]
      :compiler {
        :output-to     "web/rum.min.js"
        :optimizations :advanced
        :pretty-print  false
    }}
  ]}
)

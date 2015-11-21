(defproject rum "0.6.0"
  :description "ClojureScript wrapper for React"
  :license     { :name "Eclipse"
                 :url  "http://www.eclipse.org/legal/epl-v10.html" }
  :url         "https://github.com/tonsky/rum"

  :dependencies
  [[org.clojure/clojure        "1.7.0"   :scope "provided"]
   [org.clojure/clojurescript  "1.7.170" :scope "provided"]
   [cljsjs/react "0.14.3-0"]
   [cljsjs/react-dom "0.14.3-1"]
   [sablono "0.4.0"]]

  :global-vars
  { *warn-on-reflection* true }

  :plugins [ [lein-cljsbuild "1.1.1"] ]

  :cljsbuild
  { :builds
    [{ :id "advanced"
       :source-paths ["src" "examples"]
       :compiler
       { :main           rum.examples
         :output-to      "target/main.js"
         :optimizations  :advanced
         :source-map     "target/main.js.map"
         :pretty-print   false
         :compiler-stats true
         :parallel-build true }}
     { :id "none"
       :source-paths ["src" "examples"]
       :compiler
       { :main           rum.examples
         :output-to      "target/main.js"
         :output-dir     "target/none"
         :asset-path     "target/none"
         :optimizations  :none
         :source-map     true
         :compiler-stats true
         :parallel-build true }}]})

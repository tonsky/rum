(defproject rum "0.4.1"
  :description "ClojureScript wrapper for React"
  :license     { :name "Eclipse"
                 :url  "http://www.eclipse.org/legal/epl-v10.html" }
  :url         "https://github.com/tonsky/rum"
  
  :dependencies
  [ [org.clojure/clojure        "1.7.0"   :scope "provided"]
    [org.clojure/clojurescript  "1.7.122" :scope "provided"]
    [cljsjs/react               "0.13.3-1"]
    [sablono                    "0.3.6"] ]
  
  :global-vars
  { *warn-on-reflection* true }

  :plugins [ [lein-cljsbuild "1.1.0"] ]

  :cljsbuild
  { :builds
    [{ :id "advanced"
       :source-paths ["src" "examples"]
       :compiler
       { :main           examples
         :output-to      "target/main.js"
         :optimizations  :advanced
         :source-map     "target/main.js.map"
         :pretty-print   false
         :compiler-stats true }}
     { :id "none"
       :source-paths ["src" "examples"]
       :compiler
       { :main           examples
         :output-to      "target/main.js"
         :output-dir     "target/none"
         :asset-path     "target/none"
         :optimizations  :none
         :source-map     "target/main.js.map"
         :compiler-stats true }}]})

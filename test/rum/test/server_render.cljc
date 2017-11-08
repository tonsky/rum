(ns rum.test.server-render
  (:require
    [rum.core :as rum]
    [clojure.string :as str]
  #?@(:clj  [[clojure.test :refer [deftest is are testing]]
             [clojure.java.shell :as shell]
             [clojure.java.io :as io]
             [clj-diffmatchpatch :as diff]]
      :cljs [[cljs.test :refer-macros [deftest is are testing]]
             [cljsjs.react.dom.server]])))

(rum/defc comp-simple []
  [:div
    [:div "A"
      [:span "A1"]
      [:span "A2"]]
    [:div "B"]
    [:div "C" "D"]
    [:div "E"
      [:span "E1"]]
    [:div nil]
    [:div nil "F"]
    [:div {} ((constantly nil)) "G"]])


(rum/defc comp-tag []
  [:div.header#up "test"])


(rum/defc comp-list []
  [:ul [:li {:key "F"}] [:li {:key "M"}]])


(rum/defc comp-lists []
  [:div
    [:.a (list [:.b])]
    [:.c "d" (list [:.e])]
    [:.f "g" (list [:.h]) "i"]])


(rum/defc comp-header []
  [:ul.nav__content
    (list [:li.menu-item {:key "F"} "Женщинам"]
          [:li.menu-item {:key "M"} "Мужчинам"])
    [:li.menu-item {:key "outlet"} "Outlet"]])


(rum/defc comp-nil1 []
  "In this case nil will be not counted against reactid"
  [:div {:class "parent"}
   nil
   [:div.child]])


(rum/defc comp-nil2 []
  "In this case *both* nils will be counted against reactid"
  [:div {:class "parent"}
   nil
   [:div.child]
   (list
     nil
     [:div.child2])])


(rum/defc comp-nothing []
  nil)


(rum/defc comp-nothing2 []
  [:div
    [:div (comp-nothing)]
    [:div "a" (comp-nothing)]
    [:div (comp-nothing) "b"]
    [:div "a" (comp-nothing) "b" [:span "x"]]
    [:div [:.a] (comp-nothing) [:.b]]
    [:div (rum/with-key (comp-nothing) "K")]])


(rum/defc comp-span []
  [:div
   "test"
   "passed"])


(rum/defc comp-campaign []
  [:div#today.content.wrapper
    (list 
      [:div.banner { :class " big "
                     :style { :background-image "url(123)" }
                     :key   "campaign-20871" }
        [:a.banner__item-link { :href "/catalogue/s-10079-colin-s/" }]]
      
      [:div.banner { :class " "
                     :key "banner-:promo" }
        [:a.banner__item-link { :href nil, :target "_blank" }]]
      
      [:div.banner { :class " medium "
                     :style { :background-image "url(321)" }
                     :key   "campaign-20872" }
        [:a.banner__item-link { :href "/catalogue/s-10089-rinascimento/" }]])])


(rum/defc comp-styles []
  [:div 
    [:div.a { :style {} }]
    [:div.b { :style { :background-color nil } }]
    [:div.c { :style { :background-color "" } }]
    [:div.d { :style
              { :background-image "url(\"123\")" ;; should escape quotes
                :line-height      24         ;; unitless, should not add 'px'
                :-webkit-box-flex 3          ;; prefixed unitless
                :margin-top       17         ;; should add 'px'
                :margin-right     17.1
                :margin-left      0 }}]      ;; no 'px' added to 0
    [:div.e { :style
              { :border-width     " 1  "     ;; trim  numeric & append 'px'
                :padding-right    " 1.2 "    
                :padding-bottom   "1em"      ;; do not add 'px' if unit already specified
                :text-align       " left  "  ;; trim non-numeric values
                :flex-grow        " 1  " }}] ;; trim unitless values
    [:div.f { :style
              { :background-image "url('123')" ;; should escape quotes
                :fontWeight       10      ;; should convert from react-style properties to CSS
                "WebkitFlex"      1       ;; prefixed react-style prop
                "msFlex"          1       ;; prefixed react-style prop (lowecase ms)
                "zIndex"          1 }}]]) ;; accept strings too


(rum/defc comp-attrs []
  [:div
    [:div { :data-attr-ibute   "b"   ;; should not touch data-* and aria* attr names
            :aria-checked      "c"
            :form-enc-type     "text/plain" ;; should normalize (remove dashes)
            :checked           false        ;; nil and false attrs not printed
            :allow-full-screen true         ;; true printed as attr=""
            :href              "/a=b&c=d" } ]]) ;; & should be properly escaped


(rum/defc comp-attrs-order []
  [:div
    [:a { :title  "a"
          :alt    "b"
          :rel    "c"
          :target "d"
          :src    "e" }]
    [:a { :src    "a"
          :target "b"
          :rel    "c"
          :alt    "d"
          :title  "e" }]
    [:a       { :title "a" :class "b"       :rel "d" }]
    [:a       { :title "a" :class ["b" "c"] :rel "d" }]
    [:a.clazz { :title "a" :class "b"       :rel "d" }]
    [:a.clazz { :title "a" :class ["b" "c"] :rel "d" }]
    [:a.clazz#id { :title "a" }]
    [:a#id.clazz { :title "a" }]
    [:a.clazz#id { :title "a" :class "b" }]
    [:a#clazz.id { :title "a" :class "b" }]])


(rum/defc comp-classes []
  [:div
    [:div { :class [nil] }]
    [:div { :class :c3 }]
    [:div { :class [:c3 :c4] }]        ;; list form
    [:div { :class "c3" }]             ;; string form
    [:div { :class ["c3" "c4"]}]
    [:div { :class [" c3  " "  c4 "]}] ;; trimming
    [:div { :class [:c3 nil :c4] }]    ;; nils are not removed
    [:div { :class [:c2 :c3]}]         ;; removing duplicates
    [:.c1 { :class nil }]
    [:.c1 { :class (when false "...") }] ;; see #99
    [:.c1.c2 { :class :c3 }]
    [:.c1.c2 { :class [:c3 :c4] }]        ;; list form
    [:.c1.c2 { :class "c3" }]             ;; string form
    [:.c1.c2 { :class ["c3" "c4"]}]
    [:.c1.c2 { :class [" c3  " "  c4 "]}] ;; trimming
    [:.c1.c2 { :class [:c3 nil :c4] }]    ;; nils are not removed
    [:.c1.c2 { :class [:c2 :c3]}]])       ;; not removing duplicates


(rum/defc comp-reactid [] ;; check for proper reactid allocation
  [:div
    (map (fn [i] [:div (str i)]) (range 100))])


(rum/defc comp-html []
  [:div {:dangerouslySetInnerHTML {:__html "<span>test</span>"}}])


(rum/defc comp-inputs []
  [:div
    [:input#id {:class "x" :type "text" :auto-complete "off"}]
    [:input {:type "text"     :default-value "x"}]
    [:input {:type "checkbox" :default-checked true}]
    [:input {:type "radio"    :default-checked true}]
    [:select {:default-value "A"}
      [:option {:value "A"} "Apple"]
      [:option {:value "B"} "Banana"]]
    [:select {:value "A"}
      [:option#id.class {:value "A"} "Apple"]
      [:option#id.class {:value "B"} "Banana"]]
    [:textarea {:value "text"}]
    [:textarea "text"]
    [:textarea {:default-value "text"}]])


(rum/defc comp-svg []
  [:svg.cclogo
    { :width 100
      :height 100
      :view-box "0 232.5 333.2 232.5" ;; should be rendered as viewBox
      :vector-effect "effect"         ;; should be rendered as vector-effect
      :version "1.1"
      :dangerouslySetInnerHTML {:__html "[...tons of raw SVG removed...]"} }])


(rum/defc comp-aria []
  [:div
   { :aria-hidden   true
     :aria-readonly false
     :aria-disabled "true" 
     :aria-checked  "false" }])


(def components
  { "simple"      comp-simple
    "tag"         comp-tag
    "list"        comp-list
    "lists"       comp-lists
    "header"      comp-header
    "nil1"        comp-nil1
    "nil2"        comp-nil2
    "nothing"     comp-nothing
    "nothing2"    comp-nothing2
    "span"        comp-span
    "campaign"    comp-campaign
    "styles"      comp-styles
    "attrs"       comp-attrs
    "attrs-order" comp-attrs-order
    "classes"     comp-classes
    "reactid"     comp-reactid
    "html"        comp-html
    "inputs"      comp-inputs
    "svg"         comp-svg
    "aria"        comp-aria})


(def render-dir "target/server_render_test")


#?(:cljs
(defn ^:export react_render_html
  "Renders components with ReactDOMServer.renderToString
   and saves them to render-dir"
  [write-fn]
  (enable-console-print!)
  (doseq [[name ctor] components
          :let [html (js/ReactDOMServer.renderToString (ctor))
                path (str render-dir "/" name ".html")]]
    (println "  writing" path)
    (write-fn path html))))


#?(:clj
(defn exec [& cmd]
  (testing cmd
    (println "Running" (str "\"" (str/join " " cmd) "\""))
    (let [{:keys [exit out err]} (apply shell/sh cmd)]
      (is (= exit 0))
      (when-not (str/blank? err)
        (binding [*out* *err*]
          (println err)))
      (when-not (str/blank? out)
        (println out))))))

#?(:clj
(defn diff [s1 s2]
  (->>
    (diff/wdiff s1 s2)
    (map (fn [[op text]]
           (case op
             :delete (str "\033[37;41;1m" text "\033[0m")
             :insert (str "\033[37;42;1m" text "\033[0m")
             :equal  text)))
    (str/join))))
            

#?(:clj
(deftest test-server-render
  ;; run cljsbuid to get target/test.js
  (exec "lein" "with-profile" "dev" "cljsbuild" "once" "test")
  (doseq [f (reverse (file-seq (io/file render-dir)))]
    (.delete ^java.io.File f))
  (.mkdir (io/file render-dir))
  ;; run react_render_html using node
  (exec "node" "test/rum/test/react_render_html.js")
  (doseq [[name ctor] components]
    (testing name
      ;; compare html rendered with react 
      ;;      to html rendered with rum/render-html
      (let [react-html (slurp (str render-dir "/" name ".html"))
            rum-html   (rum/render-html (ctor))]
        (is (= react-html rum-html) (diff react-html rum-html)))))))

(ns rum.server-render-test
  (:require
    [rum.core :as rum]
    [clojure.string :as str]
  #?@(:clj  [[clojure.test :refer [deftest is are testing]]
             [clojure.java.shell :as shell]]
      :cljs [[cljs.test :refer-macros [deftest is are testing]]
             [cljsjs.react.dom.server]])))


(rum/defc comp-tag []
  [:div.header#up "test"])


(rum/defc comp-list []
  [:ul [:li {:key "F"}] [:li {:key "M"}]])


(rum/defc comp-header []
  [:ul.nav__content
    (list [:li.menu-item {:key "F"} "Женщинам"]
          [:li.menu-item {:key "M"} "Мужчинам"])
    [:li.menu-item {:key "outlet"} "Outlet"]])

(rum/defc comp-nil []
  [:div {:class "parent"}
   nil
   [:div.child]])

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
    [:div { :style
            { :background-image "url(\"123\")" ;; should escape quotes
              :line-height      24         ;; unitless, should not add 'px'
              :-webkit-box-flex 3          ;; prefixed unitless
              :margin-top       17         ;; should add 'px'
              :border-width     " 1  "     ;; trim  numeric & append 'px'
              :padding-bottom   "1em"      ;; do not add 'px' if unit already specified
              :text-align       " left  "  ;; don’t trim non-numeric values
              :flex-grow        " 1  " }}] ;; do not trim unitless values
    [:div { :style
            { :background-image "url('123')" ;; should escape quotes
              :fontWeight       10      ;; should convert from react-style properties to CSS
              "WebkitFlex"      1       ;; prefixed react-style prop
              "msFlex"          1       ;; prefixed react-style prop (lowecase ms)
              "zIndex"          1 }}]]) ;; accept strings too


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
    [:a#clazz.id { :title "a" :class "b" }]
])


(rum/defc comp-classes []
  [:div
    [:div { :class :c3 }]
    [:div { :class [:c3 :c4] }]        ;; list form
    [:div { :class "c3" }]             ;; string form
    [:div { :class ["c3" "c4"]}]
    [:div { :class [" c3  " "  c4 "]}] ;; trimming
    [:div { :class [:c3 nil :c4] }]    ;; nils are not removed
    [:div { :class [:c2 :c3]}]         ;; removing duplicates
    [:.c1.c2 { :class :c3 }]
    [:.c1.c2 { :class [:c3 :c4] }]        ;; list form
    [:.c1.c2 { :class "c3" }]             ;; string form
    [:.c1.c2 { :class ["c3" "c4"]}]
    [:.c1.c2 { :class [" c3  " "  c4 "]}] ;; trimming
    [:.c1.c2 { :class [:c3 nil :c4] }]    ;; nils are not removed
    [:.c1.c2 { :class [:c2 :c3]}]])       ;; removing duplicates


(rum/defc comp-reactid [] ;; check for proper reactid allocation
  [:div
    (map (fn [i] [:div (str i)]) (range 100))])

  
(def components
  { "tag"         comp-tag
    "list"        comp-list
    "header"      comp-header
    "nil"         comp-nil
    "campaign"    comp-campaign
    "styles"      comp-styles
    "attrs-order" comp-attrs-order
    "classes"     comp-classes
    "reactid"     comp-reactid })


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
(deftest test-server-render
  ;; run cljsbuid to get target/test.js
  (exec "lein" "with-profile" "server" "cljsbuild" "once" "test")
  (exec "rm" "-rf" render-dir)
  (exec "mkdir" render-dir)
  ;; run react_render_html using node
  (exec "node" "test/rum/react_render_html.js")
  (doseq [[name ctor] components]
    (testing name
      ;; compare html rendered with react 
      ;;      to html rendered with rum/render-html
      (let [react-html         (slurp (str render-dir "/" name ".html"))
            [_ react-root-key] (re-find #"data-reactid=\"\.([^\"]+)\"" react-html)
            [_ react-checksum] (re-find #"data-react-checksum=\"([^\"]+)\"" react-html)
            rum-html           (rum/render-html (ctor) {:root-key react-root-key})]
        (is (= react-html rum-html)))))))

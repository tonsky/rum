(ns rum.examples-page
  (:require
   [rum.core :as rum]
   [rum.examples.core :as core]
   [rum.examples.timer-reactive :as timer-reactive]
   [rum.examples.timer-static   :as timer-static]
   [rum.examples.controls       :as controls]
   [rum.examples.binary-clock   :as binary-clock]
   [rum.examples.board-reactive :as board-reactive]
   [rum.examples.bmi-calculator :as bmi-calculator]
   [rum.examples.inputs         :as inputs]
   [rum.examples.refs           :as refs]
   [rum.examples.local-state    :as local-state]
   [rum.examples.keys           :as keys]
   [rum.examples.self-reference :as self-reference]
   [rum.examples.multiple-return :as multiple-return]
   [rum.examples.errors          :as errors]
   [rum.examples.js-components   :as js-components]))

(defn ->str [v]
  (cond
    (keyword? v) (name v)
    (number? v) (str v "px")
    :else v))

(defn css [styles]
  (reduce
    (fn [ret [selector styles]]
      (str ret
           (->> styles
                (reduce-kv #(str %1 (name %2) ":" (->str %3) ";") "{")
                (str (name selector)))
           "}"))
    ""
    styles))

(defn style [& v]
  [:style {:dangerouslySetInnerHTML {:__html (css v)}}])

(defn head []
  [:head
   [:meta {:http-equiv "content-type" :content "text/html;charset=UTF-8"}]
   [:title "Rum test page"]
   [:link {:href "https://fonts.googleapis.com/css2?family=Fira+Code:wght@300;400;500;600;700&display=swap"
           :ref "stylesheet"}]
   (style
     [:* {:box-sizing :border-box
          :vertical-align :top
          :text-rendering :optimizelegibility}]
     ["tr, td, table, tbody" {:padding 0
                              :margin 0
                              :border-collapse :collapse}]
     ["body, input, button" {:font "16px/20px 'Fira Code', sans-serif"}]
     [:.example {:border "2px solid #ccc"
                 :border-radius 2
                 :padding 20
                 :width 300
                 :display :inline-block
                 :height 260
                 :margin "10px 5px"
                 :line-height "28px"}]
     [:.example-title {:border-bottom "1px solid #ccc"
                       :margin "-4px 0 20px"
                       :line-height "30px"}]
     [:dt {:width 130
           :float :left}]
     ["dt, dd" {:vertical-align :bottom
                :line-height "36px"}]
     [:input {:padding "6px 6px 2px"
              :border "1px solid #CCC"}]
     [:input:focus {:outline "2px solid #a3ccf7"}]
     [:.bclock {:margin "-6px 0 0 -4px"}]
     [".bclock td, .bclock th" {:height 25
                                :font-size "12px"
                                :font-weight :normal}]
     [".bclock th" {:width 10}]
     [".bclock td" {:width 25
                    :border "4px solid white"}]
     [:.bclock-bit {:background-color "#eee"}]
     [".bclock .stats" {:text-align :left
                        :padding-left 8}]
     [:.artboard {:-webkit-user-select :none
                  :line-height "10px"}]
     [:.art-cell {:width 12
                  :height 12
                  :margin "0 1px 1px 0"
                  :display :inline-block
                  :background-color "#EEE"}]
     [".artboard .stats" {:font-size "12px"
                          :line-height "14px"
                          :margin-top 8}])])

(defn example [title & children]
  [:.example
   [:.example-title title]
   children])

(defn as-html [form]
  {:dangerouslySetInnerHTML {:__html (rum/render-html form)}})

(defn page []
  [:html
   (head)
   [:body
    (example "Timers"
      [:#timer-static (as-html (timer-static/timer-static "Static" @core/*clock))]
      [:#timer-reactive (as-html (timer-reactive/timer-reactive))])
    (example "Controls"
      [:#controls (as-html (controls/controls))])
    (example "Reactive binary clock"
      [:#binary-clock (as-html (binary-clock/binary-clock))])
    (example "Reactive artboard"
      [:#board-reactive (as-html (board-reactive/board-reactive))])
    (example "BMI Calculator"
      [:#bmi-calculator (as-html (bmi-calculator/bmi-calculator))])
    (example "Form validation"
      [:#form-validation])
    (example "Inputs"
      [:#inputs (as-html (inputs/inputs))])
    (example "Refs"
      [:#refs (as-html (refs/refs))])
    (example "Local state"
      [:#local-state (as-html (local-state/local-state "Clicks count"))])
    (example "Keys"
      [:#keys (as-html (keys/keys))])
    (example "Self-reference"
      [:#self-reference (as-html (self-reference/self-reference [:a [:b [:c :d [:e] :g]]]))])
    (example "Contexts"
      [:#context])
    (example "Custom Methods and Data"
      [:#custom-props])
    (example "Multiple Return"
      [:#multiple-return (as-html (multiple-return/ulist (multiple-return/multiple-return)))])
    (example "Portals"
      [:#portal-off-root]
      [:#portal-root])
    (example "Error boundaries"
      [:p "Server: " [:span#server-errors (as-html (errors/errors))]]
      [:p "Client: " [:span#client-errors]])
    (example "JavaScript components"
      [:#js-components (as-html (js-components/mount! nil))])
    [:script {:src "target/main.js"}]]])

(defn -main [& args]
  (println "Writing \"index.html\"")
  (->> (rum/render-static-markup (page))
       (spit "index.html")))


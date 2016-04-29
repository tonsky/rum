(ns rum.examples-page
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]
    [rum.examples.timer-forced   :as timer-forced]
    [rum.examples.timer-reactive :as timer-reactive]
    [rum.examples.timer-static   :as timer-static]
    [rum.examples.controls       :as controls]
    [rum.examples.binary-clock   :as binary-clock]
    [rum.examples.board-reactive :as board-reactive]
    [rum.examples.board-cursored :as board-cursored]
    [rum.examples.bmi-calculator :as bmi-calculator]
    [rum.examples.local-state    :as local-state]
    [rum.examples.self-reference :as self-reference]))

(def page
  (str
   "<!doctype html>"
   (rum/render-static-markup-str
    [:html
     [:head
      [:meta {:http-equiv "content-type" :content "text/html" :charset "UTF-8"}]
      [:title "Rum test page"]
      [:link {:href "http://cloud.webtype.com/css/34a9dbc8-2766-4967-a61f-35675306f239.css" :rel "stylesheet" :type "text/css"}]
      [:link {:href "style.css" :rel "stylesheet" :type "text/css"}]]
     [:body
      [:.example
       [:.example-title "Timers"]
       [:#timer-static   (rum/render-html (timer-static/timer-static "Static" @core/*clock))]
       [:#timer-forced   (rum/render-html (timer-forced/timer-forced))]
       [:#timer-reactive (rum/render-html (timer-reactive/timer-reactive))]]
      [:.example
       [:.example-title "Controls"]
       [:#controls       (rum/render-html (controls/controls))]]
      [:.example
       [:.example-title "Reactive binary clock"]
       [:#binary-clock   (rum/render-html (binary-clock/binary-clock))]]
      [:.example
       [:.example-title "Reactive artboard"]
       [:#board-reactive (rum/render-html (board-reactive/board-reactive))]]
      [:.example
       [:.example-title "Cursor artboard"]
       [:#board-cursored (rum/render-html (board-cursored/board-cursored board-cursored/*board))]]
      [:.example
       [:.example-title "BMI Calculator"]
       [:#bmi-calculator (rum/render-html (bmi-calculator/bmi-calculator))]]
      [:.example
       [:.example-title "Form validation"]
       [:#form-validation]]
      [:.example
       [:.example-title "Local state"]
       [:#local-state    (rum/render-html (local-state/local-state "Clicks count"))]]
      [:.example
       [:.example-title "Self-reference"]
       [:#self-reference (rum/render-html (self-reference/self-reference [:a [:b [:c :d [:e] :g]]]))]]
      [:.example
       [:.example-title "Contexts"]
       [:#context]]
      [:.example
       [:.example-title "Custom Methods and Data"]
       [:#custom-props]]
      [:script {:src "target/main.js" :type "text/javascript"}]]])))


(defn -main [& args]
  (println "Writing \"index.html\"")
  (spit "index.html" page))

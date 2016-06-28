(ns rum.examples-page
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]
    [rum.examples.timer-reactive :as timer-reactive]
    [rum.examples.timer-static   :as timer-static]
    [rum.examples.controls       :as controls]
    [rum.examples.binary-clock   :as binary-clock]
    [rum.examples.board-reactive :as board-reactive]
    [rum.examples.board-cursored :as board-cursored]
    [rum.examples.bmi-calculator :as bmi-calculator]
    [rum.examples.local-state    :as local-state]
    [rum.examples.keys           :as keys]
    [rum.examples.self-reference :as self-reference]))

(def page (str
"<!doctype html>
<html>
  <head>
    <meta http-equiv='content-type' content='text/html;charset=UTF-8'/>
    <title>Rum test page</title>
    <link href='http://cloud.webtype.com/css/34a9dbc8-2766-4967-a61f-35675306f239.css' rel='stylesheet' type='text/css' />
    <link href='style.css' rel='stylesheet' type='text/css'></link>
  </head>

  <body>

    <div class=example>
      <div class=example-title>Timers</div>
      <div id=timer-static>"   (rum/render-html (timer-static/timer-static "Static" @core/*clock)) "</div>
      <div id=timer-reactive>" (rum/render-html (timer-reactive/timer-reactive)) "</div>
    </div>

    <div class=example>
      <div class=example-title>Controls</div>
      <div id=controls>" (rum/render-html (controls/controls)) "</div>
    </div>

    <div class=example>
      <div class=example-title>Reactive binary clock</div>
      <div id=binary-clock>" (rum/render-html (binary-clock/binary-clock)) "</div>
    </div>

    <div class=example>
      <div class=example-title>Reactive artboard</div>
      <div id=board-reactive>" (rum/render-html (board-reactive/board-reactive)) "</div>
    </div>


    <div class=example>
      <div class=example-title>Cursor artboard</div>
      <div id=board-cursored>" (rum/render-html (board-cursored/board-cursored board-cursored/*board)) "</div>
    </div>

    <div class=example>
      <div class=example-title>BMI Calculator</div>
      <div id=bmi-calculator>" (rum/render-html (bmi-calculator/bmi-calculator)) "</div>
    </div>
    
    <div class=example>
      <div class=example-title>Form validation</div>
      <div id=form-validation></div>
    </div>

    <div class=example>
      <div class=example-title>Local state</div>
      <div id=local-state>" (rum/render-html (local-state/local-state "Clicks count")) "</div>
    </div>
            
    <div class=example>
      <div class=example-title>Keys</div>
      <div id=keys>" (rum/render-html (keys/keys)) "</div>
    </div>
    
    <div class=example>
      <div class=example-title>Self-reference</div>
      <div id=self-reference>" (rum/render-html (self-reference/self-reference [:a [:b [:c :d [:e] :g]]])) "</div>
    </div>

    <div class=example>
      <div class=example-title>Contexts</div>
      <div id=context></div>
    </div>

    <div class=example>
      <div class=example-title>Custom Methods and Data</div>
      <div id=custom-props></div>
    </div>

    
    <script src='target/main.js' type='text/javascript'></script>
  </body>
</html>"))


(defn -main [& args]
  (println "Writing \"index.html\"")
  (spit "index.html" page))


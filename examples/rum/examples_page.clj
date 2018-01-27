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
    [rum.examples.multiple-return :as multiple-return]))

(def page (str
"<!doctype html>
<html>
  <head>
    <meta http-equiv='content-type' content='text/html;charset=UTF-8'/>
    <title>Rum test page</title>
    <link href='http://cloud.webtype.com/css/34a9dbc8-2766-4967-a61f-35675306f239.css' rel='stylesheet' type='text/css' />
    <style>
      * { box-sizing: border-box; vertical-align: top; text-rendering: optimizelegibility; }
      tr, td, table, tbody { padding: 0; margin: 0; border-collapse: collapse; }

      body, input, button { font: 16px/20px 'Input Sans Narr', 'Input Sans Narrow', sans-serif;}

      .example { border: 2px solid #ccc; border-radius: 2px; padding: 20px; width: 300px; display: inline-block; height: 260px; margin: 10px 5px; line-height: 28px; }
      .example-title { border-bottom: 1px solid #ccc; margin: -4px 0 20px; line-height: 30px; }
      dt { width: 82px; float: left; }
      dt, dd { vertical-align: bottom; line-height: 36px }
      input { padding: 6px 6px 2px; border: 1px solid #CCC; }
      input:focus { outline: 2px solid #a3ccf7; }

      .bclock { margin: -6px 0 0 -4px; }
      .bclock td, .bclock th { height: 25px; font-size: 12px; font-weight: normal; }
      .bclock th { width: 10px; }
      .bclock td { width: 25px; border: 4px solid white;  }
      .bclock-bit { background-color: #EEE; }
      .bclock .stats { text-align: left; padding-left: 8px; }

      .artboard { -webkit-user-select: none; line-height: 10px; }
      .art-cell { width: 12px; height: 12px; margin: 0 1px 1px 0; display: inline-block; background-color: #EEE; }
      .artboard .stats { font-size: 12px; line-height: 14px; margin-top: 8px; }
    </style>
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
      <div class=example-title>BMI Calculator</div>
      <div id=bmi-calculator>" (rum/render-html (bmi-calculator/bmi-calculator)) "</div>
    </div>
    
    <div class=example>
      <div class=example-title>Form validation</div>
      <div id=form-validation></div>
    </div>

    <div class=example>
      <div class=example-title>Inputs</div>
      <div id=inputs>" (rum/render-html (inputs/inputs)) "</div>
    </div>
            
    <div class=example>
      <div class=example-title>Refs</div>
      <div id=refs>" (rum/render-html (refs/refs)) "</div>
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

    <div class=example> 
      <div class=example-title>Multiple Return</div> 
      <div id=multiple-return>" (rum/render-html (multiple-return/ulist (multiple-return/multiple-return))) "</div>
    </div> 

    <script src='target/main.js' type='text/javascript'></script>
  </body>
</html>"))


(defn -main [& args]
  (println "Writing \"index.html\"")
  (spit "index.html" page))


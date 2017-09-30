(ns rum.examples.custom-props
  (:require
    [goog.object :as gobj]
    [rum.core :as rum]
    [rum.examples.core :as core]))


;; Custom methods and data on the underlying React components.

(defn rand-color []
  (str "#" (-> (rand)
               (* 0xffffff)
               (js/Math.floor)
               (.toString 16))))

(def props
  {:msgData "Components can store custom data on the underlying React component."
   :msgMethod #(this-as this
                 [:div {:style {:cursor "pointer"}
                        :on-mouse-move
                        (fn [_]
                          (reset! core/*color (rand-color))
                          (gobj/set this "msgData" 
                            [:div {:style {:color @core/*color}}
                              (:msgData props)])
                          (rum/request-render this))}
                  "Custom methods too. Hover over me!"])})


(rum/defcc custom-props < {:class-properties props} [comp]
  [:div
   ;; using aget to avoid writing externs
   [:div (gobj/get comp "msgData")]
   [:div (.call (gobj/get comp "msgMethod") comp)]])


(defn mount! [mount-el]
  (rum/mount (custom-props) mount-el))

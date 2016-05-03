(ns rum.examples.custom-props
  (:require
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
                          (aset this "msgData" 
                                [:div {:style {:color @core/*color}}
                                  (:msgData props)])
                          (rum/request-render this))}
                  "Custom methods too. Hover me!"])})


(rum/defcc custom-props < {:class-properties props} [this]
  [:div
   ;; using aget to avoid writing externs
   [:div (aget this "msgData")]
   [:div ((aget this "msgMethod"))]])


(defn mount! [mount-el]
  (rum/mount (custom-props) mount-el))

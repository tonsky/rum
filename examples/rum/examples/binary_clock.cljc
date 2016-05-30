(ns rum.examples.binary-clock
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))


;; Binary clock


(def *bclock-renders (atom 0))


(rum/defc render-count < rum/reactive [ref]
  [:div.stats "Renders: " (rum/react ref)])


(rum/defc bit < rum/static [n bit]
  (swap! *bclock-renders inc)
  [:td.bclock-bit {:style (when (bit-test n bit) {:backgroundColor @core/*color}) }])


(rum/defc binary-clock < rum/reactive []
  (let [ts   (rum/react core/*clock)
        msec (mod ts 1000)
        sec  (mod (quot ts 1000) 60)
        min  (mod (quot ts 60000) 60)
        hour (mod (quot ts 3600000) 24)
        hh   (quot hour 10)
        hl   (mod  hour 10)
        mh   (quot min 10)
        ml   (mod  min 10)
        sh   (quot sec 10)
        sl   (mod  sec 10)
        msh  (quot msec 100)
        msm  (->   msec (quot 10) (mod 10))
        msl  (mod  msec 10)]
    [:table.bclock
      [:tbody
        [:tr [:td]      (bit hl 3) [:th] [:td]      (bit ml 3) [:th] [:td]      (bit sl 3) [:th] (bit msh 3) (bit msm 3) (bit msl 3)]
        [:tr [:td]      (bit hl 2) [:th] (bit mh 2) (bit ml 2) [:th] (bit sh 2) (bit sl 2) [:th] (bit msh 2) (bit msm 2) (bit msl 2)]
        [:tr (bit hh 1) (bit hl 1) [:th] (bit mh 1) (bit ml 1) [:th] (bit sh 1) (bit sl 1) [:th] (bit msh 1) (bit msm 1) (bit msl 1)]
        [:tr (bit hh 0) (bit hl 0) [:th] (bit mh 0) (bit ml 0) [:th] (bit sh 0) (bit sl 0) [:th] (bit msh 0) (bit msm 0) (bit msl 0)]
        [:tr [:th hh]   [:th hl]   [:th] [:th mh]   [:th ml]   [:th] [:th sh]   [:th sl]   [:th] [:th msh]   [:th msm]   [:th msl]]
        [:tr [:th {:col-span 8} (render-count *bclock-renders)]]]]))


#?(:cljs
(defn mount! [mount-el]
  (rum/mount (binary-clock) mount-el)))

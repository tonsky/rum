(ns mixins
  (:require
    [clojure.string :as str]
    [rum]
    [utils :refer [board-height board-width color]]))

(defn random-board []
  (let [cell-fn #(> (rand) 0.9)
        row-fn  #(vec (repeatedly board-width cell-fn))]
    (vec (repeatedly board-height row-fn))))


;; generic “atom editor” component
(rum/defc input < rum/reactive [ref]
  [:input {:type "text"
           :value (rum/react ref)
           :style {:width 100}
           :on-change #(reset! ref (.. % -target -value))}])

;; Custom mixin for updating components on timer for cases where you have
;; nothing to subscribe to
(def autorefresh {
  :did-mount (fn [state]
               (let [interval (js/setInterval #(rum/request-render (:rum/react-component state)) 1000)]
                 (assoc state ::interval interval)))
  :transfer-state (fn [old-state state]
                    (merge state (select-keys old-state [::interval])))
  :will-unmount (fn [state]
                  (js/clearInterval (::interval state)))
  })

(rum/defc board-stats < mixins/autorefresh rum/reactive [board renders]
  [:div.stats
   "Renders: "       (rum/react renders) [:br]
   "Board watches: " (count (.-watches board)) [:br]
   "Color watches: " (count (.-watches color))])

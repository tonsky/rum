(ns rum.examples.portals
  (:require
    [rum.core :as rum]
    [rum.examples.core :as core]))


(rum/defc portal [*clicks]
  [:div
    { :on-click (fn [_] (swap! *clicks inc))
      :style { :user-select "none", :cursor "pointer" }}
    "[ PORTAL Clicks: " @*clicks " ]"])


(rum/defcs root
  < (rum/local 0 ::*clicks)
  [{*clicks ::*clicks}]
  (list
    [:div
      { :on-click (fn [_] (swap! *clicks inc))
        :style { :user-select "none", :cursor "pointer" } }
      "[ ROOT Clicks: " @*clicks " ]"]
    #?(:cljs (rum/portal (portal *clicks) (core/el "portal-off-root")))))


#?(:cljs
(defn mount! [mount-el]
  (rum/hydrate (root) mount-el)))

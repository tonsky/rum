(ns rum.examples.errors
  (:require
    [rum.core :as rum]))


(rum/defc faulty-render [msg]
  (throw (ex-info msg {})))


(rum/defc faulty-mount
  < { :did-mount
      (fn [state]
        (let [[msg] (:rum/args state)]
          (throw (ex-info msg {})))) }
  [msg]
  "Some test you’ll never see")


(rum/defcs child-error
  < { :did-catch
      (fn [state error info]
        (assoc state ::error error)) }
  [{error ::error, c :rum/react-component} comp msg]
  (if (some? error)
    [:span "CAUGHT: " (str error)]
    [:span "No error: " (comp msg)]))


(rum/defc errors []
  [:span
    (child-error faulty-render "render error")
    #_(child-error faulty-mount "mount error")])


#?(:cljs
(defn mount! [mount-el]
  (rum/mount (errors) mount-el)))

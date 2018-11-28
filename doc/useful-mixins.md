# Request stuff on mount by AJAX

Components using this mixin will do an AJAX request on mount and will update themselves when they got a reply. Mixin puts an atom to the state whose value (after deref) is either nil (request pending) or returned value.

```clojure
(defn ajax-mixin [url key]
  { :will-mount
    (fn [state]
      (let [*data (atom nil)
            comp  (:rum/react-component state)]
        (ajax
          url 
          (fn [data]
            (reset! *data data)
            (rum/request-render comp)))
        (assoc state key *data))) })


(rum/defcs user-info < (ajax-mixin "/api/user/info" ::user)
  [state]
  (if-let [user @(::user state)]
    ...
    [:div "Loading..."]))
```

Customize to your taste: dynamic URL generation from component args, AJAX retries, failed state, callback on finish, deserialization.

# Debouncer

```clojure
(ns ... (:import [goog.async Debouncer]))

(defn debouncer-mixin
  "Creates a debouncer in (:debouncer state) which can be called with (.fire).
   Invokes the callback cb or invokes the first argument passed to fire.
   Usage:
   1. (debouncer-mixin 200)
      (.fire (:debouncer state) #(do-actual-action ...))

   2. (debouncer-mixin 200 #(do-an-action ...))
      (.fire (:debouncer state))"
  ([ms] (debouncer-mixin ms nil))
  ([ms cb]
   {:will-mount
    (fn debouncer-mount [state]
      (assoc state :debouncer (Debouncer. (if (nil? cb) #(%1) cb) ms)))
    :will-unmount
    (fn debouncer-unmount [state]
      (.dispose (:debouncer state))
      state)}))
```

# Install CSS styles on mount

For your root rum component, you can install CSS styles on mount and uninstall them on unmount.
This is useful if you have your (garden) styles defined in `cljc` file. In production you generate
a css file and include it with a normal `<style>` tag, in development however, you can just use this
mixin to have them automatically be applied as soon as you change any style.

```clojure
(ns ...
  (:require [goog.style :as gstyle]))

(defn install-styles-mixin
  "Installes the stylesheet when mounting. Uninstall when unmount.
   Useful for use at the very root render and use with garden in a cljc environment.
   Live update of CSS without needing to go trough figwheel :)"
  [css-str]
  {:will-mount
   (fn [st]
     (assoc st ::stylesheet (gstyle/installStyles css-str)))
   :will-unmount
   (fn [st]
     (gstyle/uninstallStyles (::stylesheet st))
     st)})

;; Then use them like so:
app < (install-styles-mixin (your-cljc/gen-css))
```

# Measure render

You can measure how long a component needs to render with this mixin:

```clojure
(defn perf-measure-mixin
  [desc]
  "Does performance measurements in development."
  {:wrap-render
   (fn wrap-render [render-fn]
     (fn [state]
       (profile
         (str "Render " desc)
         (render-fn state))))})
;; where profile is a macro like this:
(defmacro profile [k & body]
  (if macros/dev?
    `(let [k# ~k]
       (.time js/console k#)
       (let [res# (do ~@body)]
         (.timeEnd js/console k#)
         res#))
    `(do ~@body)))
```

# Keyboard shortcut

Install a keyboard shortcut that is only valid while the component is mounted:

```clojure
(defn keyboard-mixin
  "Triggers f when key is pressed while the component is mounted.
   if target is a function it will be called AFTER the component mounted
   with state and should return a dom node that is the target of the listener.
   If no target is given it is defaulted to js/window (global handler)
   Ex:
     (keyboard-mixin \"esc\" #(browse-to :home/home))"
  ([key f] (keyboard-mixin key f js/window))
  ([key f target]
   (let [target-fn (if (fn? target) target (fn [_] target))]
     {:did-mount
      (fn [state]
        (assoc state ::keyboard-listener
                     (keyboard/install-shortcut! key f false (target-fn state))))
      :will-unmount
      (fn [state]
        ((::keyboard-listener state))
        state)})))

;; where install-shortcut! is:
(ns you-tools.keyboard
  (:require [goog.events :as events]
            [goog.ui.KeyboardShortcutHandler.EventType :as EventType]
            [goog.events.KeyCodes :as KeyCodes])
  (:import  [goog.ui KeyboardShortcutHandler]))

(defn install-shortcut!
  "Installs a Keyboard Shortcut handler.
   The key is a string the trigger is a function that will receive the keyboard event as the
   first argument. If once? is true the keyboard shortcut is only fired once.
   The unregister handler is returned and can be called to unregister the listener.
   If target is not given it's attached to window."
  ([key trigger] (install-shortcut! key trigger false js/window))
  ([key trigger once?] (install-shortcut! key trigger once? js/window))
  ([key trigger once? target]
   (let [handler (new KeyboardShortcutHandler target)]
     (.registerShortcut handler (str key once?) key)
     (events/listen
              handler
              EventType/SHORTCUT_TRIGGERED
              (fn [e]
                (trigger e)
                (when once?
                  (.unregisterShortcut handler keys))))
     (fn []
       (.unregisterShortcut handler key)))))
```

# Remember state

This mixin can be used to remember the state of a component. For this to work you need to UNIQUELY identify
your component somehow. For instance, by generating some unique id from the given args to your component:

```clojure
(defn remember-state-mixin
  "Remembers the state :rum/local for a given component and swaps it back on mount.
   The given function is passed the args of the component (with apply).
   And should return a map key that is used to uniquely identify the component.

     (remember-state-mixin (fn [arg1 arg2] (:db/id arg1)))"
  [f]
  (let [store (atom {})
        key-fn (fn [state] (apply f (:rum/args state)))]
    {:will-unmount
     (fn remember-state-unmount [state]
       (swap! store assoc (key-fn state) @(:rum/local state))
       state)
     :will-mount
     (fn remember-state-mount [state]
       (let [old-state @store
             key (key-fn state)]
         (swap! (:rum/local state) merge (get old-state key {})))
       state)}))
```
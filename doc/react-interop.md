# Use different React version

Add to `project.clj`:

```
:dependencies {
  [rum "0.11.3" :exclusions [[cljsjs/react] [cljsjs/react-dom]]]
  [cljsjs/react     "16.6.0-0"]
  [cljsjs/react-dom "16.6.0-0"]
}
```

# Including React.js manually

If you want to include `react.js` yourself, then add this to `project.clj`:

```
:dependencies {
  [rum "0.11.3" :exclusions [[cljsjs/react] [cljsjs/react-dom]]]
}
```

Create two files

1. `src/cljsjs/react.cljs`:

   ```
   (ns cljsjs.react)
   ```

2. `src/cljsjs/react/dom.cljs`:

   ```
   (ns cljsjs.react.dom)
   ```

Add to your HTML the version you want:

```
<script crossorigin src="https://unpkg.com/react@16/umd/react.production.min.js"></script>
<script crossorigin src="https://unpkg.com/react-dom@16/umd/react-dom.production.min.js"></script>
```

# Using React with addons

```clj
[rum "0.11.3" :exclusions [cljsjs/react cljsjs/react-dom]]
[cljsjs/react-dom "16.2.0-3" :exclusions [cljsjs/react]]
[cljsjs/react-dom-server "16.2.0-3" :exclusions [cljsjs/react]]
[cljsjs/react-with-addons "16.2.0-3"]
```

# Profiling with [React perf](https://facebook.github.io/react/docs/perf.html)

Specify the `react-with-addons` dependency in your `project.clj` (see above ↑)

Then from within your program run:

```clj
(js/React.addons.Perf.start)
;;run your app
(js/React.addons.Perf.stop)
(js/React.addons.Perf.printWasted)
```

and results will be printed to the developer console.

# Using 3rd-party React components

## via Adapter API

Since `0.11.5` Rum provides an API to adapt React component for usage in Rum components. But you can still create components manually as described in the next section.

```clojure
(rum/defc component []
  [:div
   (rum/adapt-class js/Slider
     {:min min
      :max max
      :range true
      :defaultValue [40 60]})])
```

When rendering on JVM Rum browsides a hook to fallback rendering of JS components, so you can delegate this work to JS environment such as GraalJS or renderer a placeholder instead.

```clojure
(defn render-js-component [type-sym attrs children]
  (case type-sym
    'js/Slider (rum/render-static-markup (slider-placeholder))
    nil))

(binding [rum/*render-js-component* render-js-component]
  (component))
```

## via React.js directly

Given e.g. [react-router-transition](https://github.com/maisano/react-router-transition)

```clj
(defn route-transition [pathname children]
  (js/React.createElement js/RouteTransition
    #js { :pathname pathname
          :atEnter  #js { :opacity 0 }
          :atLeave  #js { :opacity 0 }
          :atActive #js { :opacity 1 } }
    (clj->js children)))
```

Another example [react-component/slider](https://github.com/react-component/slider)

```clj
(defn range-slider [min max]
  (js/React.createElement js/Slider #js { :min min
                                          :max max
                                          :range true
                                          :defaultValue #js [40 60] }))
```

If you want to mix 3rd-party React components with child elements using the Hiccup-like syntax, you can call directly into the library that provides it, daiquiri. This can be particularly useful for 3rd-party React components that are made to wrap your own components, like drag-and-drop plugins and so on.

```clj
(js/React.createElement js/MyComponent
  #js { }
  (daiquiri.core/html [:div [:p "Hello, world"]]))
```

**Note:** See how `defn` is used here instead of `defc`? Using `defc` would cause two components being created (e.g. `range-slider` and the `Slider` component). Because in many cases you don't need the wrapping component you can just use `defn`.

# Using Rum component in React

In order to use Rum component from React JS code you have to create gluing layer in a form of wrapping function (React component) that unwraps React's props object and passes values into Rum component as arguments.

```clojure
(rum/defc button [{:keys [on-click]} text]
  [:button {:on-click on-click}
    text])

(defn Button [^js props]
  (button {:on-click (.-onClick props)}
    (.-children props)))
```

# Get displayName of component

This might be useful for development when you want to know which component this mixin is handling:

```clojure
(ns ...
  (:require [goog.object :as gobj]))

(defn display-name
  "Returns the displayname of the component"
  [state]
  (gobj/getValueByKeys
    (:rum/react-component state)
    "constructor"
    "displayName"))
```

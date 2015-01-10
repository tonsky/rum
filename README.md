# Rum: Yet another React wrapper for ClojureScript

Rum goals:

1. Be ClojureScript-friendly
2. Provide clean, simple and straightforward API (with simpler semantics than React’s own)
3. Allow for mixing different kinds of components in a single app
4. Allow for building new kinds of components easily
5. Stay minimal

Rum is not a framework that tells you how your components should work.
Instead, it’s a library that gives you the tools, so you can build components
that fits your needs best.

## What’s in the box?

Rum provides basic tools that every React app eventually need:

* `requestAnimationFrame`-based render queue
* component id generator
* `sablono`-based syntax for generating markup
* couple of wrapper functions like `mount`, `build-class`, `element` etc.

## Using Rum

Add this to your `project.clj`:

```clojure
:dependencies [
  [org.clojure/clojurescript "0.0-2665"]
  [rum "0.1.1"]
]
```

Simplest example defines component, instantiates it and mounts it on a page:

```clojure
(ns example
  (:require [rum :include-macros true]))

(rum/defc label [n text]
  [:.label (repeat n text)])

(rum/mount (label 5 "abc") (.-body js/document))
```

For more examples, see [examples/examples.cljs](examples/examples.cljs). Live version of examples [is here](http://tonsky.me/rum/)

## Rum API

Rum provides `defc` macro (short from “define component”):

```clojure
(rum/defc [mixins]* name argvec & render-body)
 ```

`defc` defines top-level function that accepts `argvec` and returns React element that renders as specified in `render-body`.

Behind the scenes, `defc` does couple of things:

- Creates render function by wrapping `render-body` with implicit `do` and then with `sablono.core/html` macro
- Builds React class from provided mixins and render function
- Defines a top-level function `name` with arguments list `argvec`
- When called, `name` function will create new React element from built React class and pass though `argvec` so it’ll be available inside `render-body`

To mount component, use `rum/mount`:

```clojure
(rum/mount element dom-node)
```

`mount` returns mounted component. It’s safe to call it multiple times over same arguments.

Note that `mount` will not make component auto-updatable. It’s up to your code (or mixins) to make it update when you need it. Two common idioms are to mount it again:

```clojure
(add-watch state :render
  (fn [_ _ _ _]
    (rum/mount element node)))
```

or call `request-render` function:

```clojure
(let [component (rum/mount element dom-node)]
  (add-watch state :render
    (fn [_ _ _ _]
      (rum/request-render component))))
```

`request-render` does not execute rendering immediately, instead, it will place your component to render queue and re-render on `requestAnimationFrame` callback. `request-render` is preferable way to refresh component.

## Mixins

Rum comes with a couple of mixins which emulate behaviors known from `quiescent`, `om` and `reagent`. Developing your own mixin is also very simple.

`rum/static` will check if arguments of a component constructor have changed (with Clojure’s `-equiv` semantic), and if they are the same, avoid re-rendering.

```clojure
(rum/defc rum/static label [n text]
  [:.label (repeat n text)])

(rum/mount (label 1 "abc") body)
(rum/mount (label 1 "abc") body) ;; render won’t be called
(rum/mount (label 1 "xyz") body) ;; this will cause a re-render
```

`rum/reactive` will create “reactive” component that will track references used inside `render` function and auto-update when values of these references change.

```clojure
(def color (atom "#cc3333"))
(def text (atom "Hello"))

(rum/defc rum/reactive label []
  [:.label {:style {:color (rum/react color)}}
    (rum/react text)])
    
(rum/mount (label) (.-body js/document))
(reset! text "Good bye") ;; will cause re-rendering
(reset! color "#000")    ;; and another one
```

`rum/react` function used in this example works as `deref`, and additionally adds watch on that reference.

Finally, `rum/cursored` is a mixin that will track changes in references passed as arguments:

```clojure
(rum/defc rum/cursored label [color text]
  [:.label {:style {:color @color}} @text])
```

Note that `cursored` mixin creates passive component: it will not track any values, and will only compare arguments when re-created by its parent. Additional `rum/cursored-watch` mixin will add watches on every `IWatchable` in arguments list:

```clojure
(rum/defc [rum/cursored rum/cursored-watch] body [color text]
  (label color text))

(rum/mount (body color text) (.-body js/document))

;; will cause re-rendering of body and label
(reset! text "Good bye")

;; and another one
(reset! color "#000")
```

Rum also provides cursors, an abstraction that provides atom-like interface to subtrees inside an atom:

```clojure
(def state (atom {:color  "#cc3333"
                  :label1 "Hello"
                  :label2 "Goodbye"}))

(rum/defc rum/cursored label [color text]
  [:.label {:style {:color @color}} @text])

(rum/defc [rum/cursored rum/cursored-watch] body [state]
  [:div
    (label (rum/cursor state [:color]) (rum/cursor state [:label1]))
    (label (rum/cursor state [:color]) (rum/cursor state [:label2]))])

(rum/mount (body state) (.-body js/document))

;; will cause re-rendering of second label only
(swap! state assoc :label2 "Good bye")

;; both will be re-rendered
(swap! state assoc :color "#000")

;; cursors can be swapped and reseted just like atoms
(reset! (rum/cursor state [:label1]) "Hi")
```

Cursors implement `IAtom` and `IWatchable` and interface-wise are drop-in replacement for regular atoms. They can be used with `reactive` components as well.

Beauty of Rum approach is that you can combine multiple mixins inside single component and you can use completely different classes around the tree. You can have top-level `reactive` component, couple of nested `static` ones, then a component which updates every second, and inside it a `cursored` one. Decomplected, powerful, simple.

## Rum component model

Rum defines classes and components. Internally they are React’s classes and components.

Each component in Rum has state associated with it. State is just a CLJS map with:

* `:rum/react-component` — link to React component/element object
* `:rum/id` — unique component id
* everything mixins are using for they internal bookkeeping 
* anything your own code put here

Reference to current state is stored as `volatile!` boxed value at `props[":rum/state"]`.
Effectively state is mutable, but components do not change volatile reference directly,
instead all lifecycle functions accept and return state value.

Classes define component behavior, including render function. Class is built from multiple mixins. 

Mixins are basic building blocks for designing new components behaviors in Rum. Each mixin is just a map of one or more of following functions:

```clojure
{ :init            ;; state, props     ⇒ state
  :will-mount      ;; state            ⇒ state
  :did-mount       ;; state            ⇒ state
  :transfer-state  ;; old-state, state ⇒ state
  :should-update   ;; old-state, state ⇒ boolean
  :render          ;; state            ⇒ [pseudo-dom state]
  :wrap-render     ;; render-fn        ⇒ render-fn
  :will-unmount    ;; state            ⇒ state }
```

Imagine a class built from N mixins. When lifecycle event happens in React (e.g. `componentDidMount`), all `:did-mount` functions from first mixin to last will be invoked one after another, threading current state value through them. State returned from last `:did-mount` mixin will be stored in volatile state reference by Rum.

Rendering is modeled differently. There must be single `:render` function that accepts state and return 2-vector of dom and new state. If mixin wants to modify render behavior, it should provide `:wrap-render` fn that accepts render function and returns modified render function (similar to ring middlewares). `:wrap-render` fns are applied from left to right, e.g. original `:render` is first passed to first `:wrap-render` function, result is then passed to second one and so on.

## Writing your own mixin

Sample mixin that forces re-render every second:

```clojure
(def autorefresh-mixin {
  :did-mount (fn [state]
               (let [comp      (:rum/react-component state)
                     callback #(rum/request-render comp)
                     interval  (js/setInterval callback 1000)]
                 (assoc state ::interval interval)))
  :will-unmount (fn [state]
                  (js/clearInterval (::interval state)))})

(rum/defc autorefresh-mixin timer []
  [:div.timer (.toISOString (js/Date.))])
```

## How it all fits together

Imagine you have simple render function:

```clojure
(defn render-label [text]
  (sablono.core/html
    [:div.label text]))
```

To convert it to React component, you create a mixin:

```clojure
(def label-mixin {
  {:render (fn [state]
              [(render-label (:text state)) state])}})
```

Then you build React class from this single mixin:

```clojure
(def label-class (rum/build-class label-mixin))
```

And define simple wrapper that creates React element from that class:

```clojure
(defn label-ctor [text]
  (rum/element label-class {:text text}))
```

Finally, you call ctor to get instance of element and mount it somewhere on a page:

```clojure
(rum/mount (label-ctor "Hello") (.-body js/document))
```

This is a detailed breakdown of what happens inside of Rum. By using `rum/component`, everything can be simplified to a much more compact code:

```clojure
(let [ctor (rum/component render-label)]
  (rum/mount (ctor "Hello") (.-body js/document)))
```

Or, with a macro:

```clojure
(rum/defc label [text]
  [:div.label text])
  
(rum/mount (label "Hello") (.-body js/document))
```

## Changes

### 0.1.1

- Fixed a bug when render-loop tried to `.forceUpdate` unmounted elements
- Fixed a cursor leak bug in `reactive` mixin
- Removed `:should-update` from `reactive`, it now will be re-rendered if re-created by top-level element
- Combine `reactive` with `static` to avoid re-rendering if component is being recreated with the same args

## Acknowledgements

Rum was build on inspiration from [Quiescent](https://github.com/levand/quiescent), [Om](https://github.com/swannodette/om) and [Reagent](https://github.com/reagent-project/reagent).

All heavy lifting done by [React](http://facebook.github.io/react/) and [ClojureScript](https://github.com/clojure/clojurescript).

## License

Copyright © 2014 Nikita Prokopov

Licensed under Eclipse Public License (see [LICENSE](LICENSE)).

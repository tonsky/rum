# Rum: simple, flexible, extensible React wrapper for CLJS

<p align="center"><img src="https://dl.dropboxusercontent.com/u/561580/imgs/rum_logo.svg" style="height: 400px;"></p>

Rum goals:

1. Be ClojureScript-friendly
2. Provide clean, simple and straightforward API (with simpler semantics than React’s own)
3. Allow for mixing different kinds of components in a single app
4. Allow for building new kinds of components easily
5. Stay minimal

Rum is not a framework that tells you how your components should work.
Instead, it’s a library that gives you the tools, so you can build components
that fit your needs best.

## Comparison to other frameworks

All ClojureScript frameworks: Om, Reagent and even Quiescent came with built-in component behaviour model. They do not allow to change that without rewriting internals. Rum was designed to specifically address that problem. Rum doesn’t sell you one true component model. Instead, contract on custom component building (mixins) is also considered to be part of API in Rum.

Rum has two levels of API. On lower level we have tools to build your own component behaviours: low-level details are well-defined and open for extensions. Thanks to that Rum is more customizable, integration with third-party models is simpler (you use storage/data model you want and write component to support that, unlike other solutions which dictate how to store app state), and you can mix different kinds of components in one app.

On higher level, Rum comes with already-built types of components which emulate behavior found in Om, Reagent and Quiescent. They were built using the same public API any Rum user can use. No internals hacking. I think it means abstraction is good enough and decomplection was made in the right place. I’m also very proud they take about 10-30 lines of code each.

Rum idea is not to lock you down to a single storage model. Sometimes your dataflow is trickier that just atoms, e.g. you need components to react to DataScript events, core.async channels, ajax/websocket/webworker callbacks. In that case Rum provides well-defined API and set of basic building blocks to write components you need to.

## What’s in the box?

Rum provides basic tools that every React app eventually need:

* `requestAnimationFrame`-based render queue
* component id generator
* `sablono`-based syntax for generating markup
* couple of wrapper functions like `mount`, `build-class`, `element` etc.

## Using Rum <a href="https://gitter.im/tonsky/rum?utm_source=badge&amp;utm_medium=badge&amp;utm_campaign=pr-badge&amp;utm_content=badge"><img src="https://camo.githubusercontent.com/da2edb525cde1455a622c58c0effc3a90b9a181c/68747470733a2f2f6261646765732e6769747465722e696d2f4a6f696e253230436861742e737667" alt="Gitter" data-canonical-src="https://badges.gitter.im/Join%20Chat.svg" style="max-width:100%;"></a>

1. Add `[rum "0.6.0"]` to dependencies
2. `(require '[rum.core :as rum])`.

Simplest example defines component, instantiates it and mounts it on a page:

```clojure
(ns example
  (:require [rum.core :as rum]))

(rum/defc label [n text]
  [:.label (repeat n text)])

(rum/mount (label 5 "abc") js/document.body)
```

For more examples, see [examples/rum/examples.cljs](examples/rum/examples.cljs). Live version of examples [is here](http://tonsky.me/rum/)

## Talks

- [Norbert Wójtowicz talk at Lambda Days 2015](https://vimeo.com/122316380) where he explains benefits of web development with ClojureScript and React, and how Rum emulates all main ClojureScript frameworks

## Examples

- DataScript Chat sample app: [github.com/tonsky/datascript-chat](https://github.com/tonsky/datascript-chat)
- DataScript ToDo sample app: [github.com/tonsky/datascript-todo](https://github.com/tonsky/datascript-todo)
- DataScript Menu example: [github.com/tonsky/datascript-menu](https://github.com/tonsky/datascript-menu)

## Libraries

- [Reforms](http://bilus.github.io/reforms/)

## Who’s using Rum?

- [Cognician](https://cognician.com/), coaching platform
- [PartsBox.io](https://partsbox.io/), inventory management
- [UXBox](http://uxbox.github.io/)

## Rum API

Rum provides `defc` macro (short from “define component”):

```clojure
(rum/defc name doc-string? [< mixins+]? [params*] render-body+)
```

`defc` defines top-level function that accepts `argvec` and returns React element that renders as specified in `render-body`.

Behind the scenes, `defc` does couple of things:

- Creates render function by wrapping `render-body` with implicit `do` and then with `sablono.core/html` macro
- Builds React class from that render function and provided mixins
- Using that class, generates constructor fn [params]->ReactElement
- Defines a top-level var `name` and puts constructor fn there

When called, `name` function will create new React element from built React class and pass through `argvec` so it’ll be available inside `render-body`

To mount component, use `rum.core/mount`:

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

`rum.core/static` will check if arguments of a component constructor have changed (with Clojure’s `-equiv` semantic), and if they are the same, avoid re-rendering.

```clojure
(rum/defc label < rum/static [n text]
  [:.label (repeat n text)])

(rum/mount (label 1 "abc") body)
(rum/mount (label 1 "abc") body) ;; render won’t be called
(rum/mount (label 1 "xyz") body) ;; this will cause a re-render
```

`rum.core/local` creates an atom that can be used as per-component local state. When you `swap!` or `reset!` this atom, component will be re-rendered automatically. Atom can be found in state under `:rum/local` key:

```clojure
(rum/defcs stateful < (rum/local 0) [state title]
  (let [local (:rum/local state)]
    [:div
     {:on-click (fn [_] (swap! local inc))}
     title ": " @local]))

(rum/mount (stateful "Clicks count") js/document.body)
```

Note that we used `defcs` instead of `defc` to get state as first argument to `render`. Also note that `rum.core/local` is not a mixin value, instead, it’s a function, generator-like: it takes initial value and returns mixin.

`rum.core/reactive` will create “reactive” component that will track references used inside `render` function and auto-update when values of these references change.

```clojure
(def color (atom "#cc3333"))
(def text (atom "Hello"))

(rum/defc label < rum/reactive []
  [:.label {:style {:color (rum/react color)}}
    (rum/react text)])
    
(rum/mount (label) js/document.body)
(reset! text "Good bye") ;; will cause re-rendering
(reset! color "#000")    ;; and another one
```

`rum.core/react` function used in this example works as `deref`, and additionally adds watch on that reference.

Finally, `rum.core/cursored` is a mixin that will track changes in references passed as arguments:

```clojure
(rum/defc label < rum/cursored [color text]
  [:.label {:style {:color @color}} @text])
```

Note that `cursored` mixin creates passive component: it will not react to any changes in references by itself, and will only compare arguments when re-created by its parent. Additional `rum.core/cursored-watch` mixin will add watches on every `IWatchable` in arguments list:

```clojure
(rum/defc body < rum/cursored rum/cursored-watch [color text]
  (label color text))

(rum/mount (body color text) js/document.body)

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

(rum/defc label < rum/cursored [color text]
  [:.label {:style {:color @color}} @text])

(rum/defc body < rum/cursored rum/cursored-watch [state]
  [:div
    (label (rum/cursor state [:color]) (rum/cursor state [:label1]))
    (label (rum/cursor state [:color]) (rum/cursor state [:label2]))])

(rum/mount (body state) js/document.body)

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
* everything mixins are using for their internal bookkeeping
* anything you’ve put there (feel free to populate state with your own stuff!)

Reference to current state is stored as `volatile!` boxed value at `state[":rum/state"]`.
Effectively state is mutable, but components do not change volatile reference directly,
instead all lifecycle functions accept and return state value.

Classes define component behavior, including render function. Class is built from multiple mixins. 

Mixins are basic building blocks for designing new components behaviors in Rum. Each mixin is just a map of one or more of following functions:

```clojure
{ :init                 ;; state, props     ⇒ state
  :will-mount           ;; state            ⇒ state
  :did-mount            ;; state            ⇒ state
  :transfer-state       ;; old-state, state ⇒ state
  :should-update        ;; old-state, state ⇒ boolean
  :will-update          ;; state            ⇒ state
  :render               ;; state            ⇒ [pseudo-dom state]
  :wrap-render          ;; render-fn        ⇒ render-fn
  :did-update           ;; state            ⇒ state
  :will-unmount         ;; state            ⇒ state 
  :child-context        ;; state            ⇒ child-context }
```

To define arbitrary properties and methods on a component class, specify `:class-properties` map:

```clojure
{ :class-properties { ... } }
```

Imagine a class built from N mixins. When lifecycle event happens in React (e.g. `componentDidMount`), all `:did-mount` functions from first mixin to last will be invoked one after another, threading current state value through them. State returned from last `:did-mount` mixin will be stored in volatile state reference by Rum. Similarly, `context` maps from multiple mixins are combined into one map.

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
  :transfer-state (fn [old-state state]
                    (merge state (select-keys old-state [::interval])))
  :will-unmount (fn [state]
                  (js/clearInterval (::interval state)))})

(rum/defc timer < autorefresh-mixin []
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
(def label-class (rum/build-class [label-mixin] "label-class"))
```

And define simple wrapper that creates React element from that class:

```clojure
(defn label-ctor [text]
  (rum/element label-class {:text text} nil))
```

Finally, you call ctor to get instance of element and mount it somewhere on a page:

```clojure
(rum/mount (label-ctor "Hello") js/document.body)
```

This is a detailed breakdown of what happens inside of Rum. By using `rum/defc`, everything can be simplified to a much more compact code:

```clojure
(rum/defc label [text]
  [:div.label text])
  
(rum/mount (label "Hello") js/document.body)
```

## Changes

### 0.6.0

- [ BREAKING ] Updated to [React 0.14.3](https://facebook.github.io/react/blog/2015/10/07/react-v0.14.html) (thx [Andrey Antukh](https://github.com/niwinz), PR #53)

### 0.5.0

- Added `:class-properties` to define arbitrary properties on a React class (thx [Karanbir Toor](https://github.com/currentoor), PR #44)
- [ BREAKING ] Removed support for `:child-context-types` and `:context-types`. Use `{ :class-properties { :childContextTypes ..., :contextTypes ... } }` instead.

### 0.4.2

- Check for `setTimeout` in global scope instead of in window (thx [Alexander Solovyov](https://github.com/piranha), PR #43)

### 0.4.1

- Fixed bug with rum macros emitting wrong namespace. You can now require `rum.core` under any alias you want (thx [Stuart Hinson](https://github.com/stuarth), PR #42)

### 0.4.0

- [ BREAKING ] Core namespace was renamed from `rum` to `rum.core` to supress CLJS warnings

### 0.3.0

- Upgraded to React 0.13.3, Sablono 0.3.6, ClojueScript 1.7.48
- New API to access context: `child-context`, `child-context-types`, `context-types` (thx [Karanbir Toor](https://github.com/currentoor), PR #37)
- New `defcc` macro for when you only need React component, not the whole Rum state
- [ BREAKING ] Component inner state (`:rum/state`) was moved from `props` to `state`. It doesn’t change a thing if you were using Rum API only, but might break something if you were relaying on internal details
- Deprecated `rum/with-props` macro, use `rum/with-key` or `rum/with-ref` fns instead

### 0.2.7

- Allow components to refer to themselves (thx [Kevin Lynagh](https://github.com/lynaghk), pull request #30)
- Support for multi-arity render fns (issue #23)

### 0.2.6

- Added `local` mixin

### 0.2.5

- Fixed argument destructuring in defc macro (issue #22)

### 0.2.4

- `will-update` and `did-update` lifecycle methods added (thx [Andrey Vasenin](https://github.com/avasenin), pull request #18)

### 0.2.3

- Components defined via `defc/defcs` will have `displayName` defined (thx [Ivan Dubrov](https://github.com/idubrov), pull request #16)
- Not referencing `requestAnimationFrame` when used in headless environment (thx @[whodidthis](https://github.com/whodidthis), pull request #14)

### 0.2.2

- Compatibility with clojurescript 0.0-2758, macros included automatically when `(:require rum)`

### 0.2.1

- Updated deps to clojurescript 0.0-2727, react 0.12.2-5 and sablono 0.3.1

### 0.2.0

- [ BREAKING ] New syntax for mixins: `(defc name < mixin1 mixin2 [args] body...)`
- New `defcs` macro that adds additional first argument to render function: `state`
- Ability to specify `key` and `ref` to rum components via `with-props`

### 0.1.1

- Fixed a bug when render-loop tried to `.forceUpdate` unmounted elements
- Fixed a cursor leak bug in `reactive` mixin
- Removed `:should-update` from `reactive`, it now will be re-rendered if re-created by top-level element
- Combine `reactive` with `static` to avoid re-rendering if component is being recreated with the same args

## Acknowledgements

Rum was build on inspiration from [Quiescent](https://github.com/levand/quiescent), [Om](https://github.com/swannodette/om) and [Reagent](https://github.com/reagent-project/reagent).

All heavy lifting done by [React](http://facebook.github.io/react/) and [ClojureScript](https://github.com/clojure/clojurescript).

## License

Copyright © 2014–2015 Nikita Prokopov

Licensed under Eclipse Public License (see [LICENSE](LICENSE)).

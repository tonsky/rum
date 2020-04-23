<p align="center"><img src="https://s.tonsky.me/imgs/rum_logo.svg" style="height: 400px;"></p>

Rum is a client/server library for HTML UI. In ClojureScript, it works as React wrapper, in Clojure, it is a static HTML generator.

## Table of Contents

- [Principles](#principles)
- [Comparison to other frameworks](#comparison-to-other-frameworks)
- [Who’s using Rum?](#whos-using-rum)
- [Using Rum](#using-rum)
  - [API Docs](#api-docs)
  - [Defining a component](#defining-a-component)
  - [Rendering component](#rendering-component)
  - [Updating components manually](#updating-components-manually)
  - [Reactive components](#reactive-components)
  - [Component’s local state](#components-local-state)
  - [Optimizing with shouldComponentUpdate](#optimizing-with-shouldcomponentupdate)
  - [Writing your own mixin](#writing-your-own-mixin)
  - [Working with atoms](#working-with-atoms)
    - [Cursors](#cursors)
    - [Derived atoms](#derived-atoms)
  - [Interop with React](#interop-with-react)
    - [Native React component](#native-react-component)
    - [React keys and refs](#react-keys-and-refs)
    - [Accessing DOM](#accessing-dom)
    - [Custom class properties](#custom-class-properties)
    - [React context](#react-context)
    - [Hooks](#react-hooks)
  - [Server-side rendering](#server-side-rendering)
- [Support](#support)
  - [Talks](#talks)
  - [App templates](#app-templates)
  - [Libraries](#libraries)
  - [Examples](#examples)
- [Acknowledgements](#acknowledgements)
- [License](#license)

### Principles

**Simple semantics**: Rum is arguably smaller, simpler and more straightforward than React itself.

**Decomplected**: Rum is a library, not a framework. Use only the parts you need, throw away or replace what you don’t need, combine different approaches in a single app, or even combine Rum with other frameworks.

**No enforced state model**: Unlike Om, Reagent or Quiescent, Rum does not dictate where to keep your state. Instead, it works well with any storage: persistent data structures, atoms, DataScript, JavaScript objects, localStorage or any custom solution you can think of.

**Extensible**: the API is stable and explicitly defined, including the API between Rum internals. It lets you build custom behaviours that change components in significant ways.

**Minimal codebase**: You can become a Rum expert just by reading its source code (~900 lines).

### Comparison to other frameworks

Rum:

- does not dictate how to store your state,
- has server-side rendering,
- is much smaller.

### Who’s using Rum?

- [Cognician](https://www.cognician.com), coaching platform
- [Attendify](https://attendify.com), mobile app builder
- [PartsBox.io](https://partsbox.io), inventory management
- [kasta.ua](https://kasta.ua), online marketplace
- [ChildrensHeartSurgery.info](http://childrensheartsurgery.info), heart surgery statistics
- [Mighty Hype](http://mightyhype.com/), cinema platform (server-side rendering)
- [БезопасныеДороги.рф](https://xn--80abhddbmm5bieahtk5n.xn--p1ai/), road data aggregator
- [TourneyBot](https://github.com/oakmac/tourney-bot), frisbee tournament app
- [PurposeFly](https://www.purposefly.com/), HR 2.0 platform
- [Simply](https://www.simply.co.za), Simple direct life insurance
- [Oscaro.com](https://www.oscaro.com), online autoparts retailer
- [Lupapiste](https://github.com/lupapiste/lupapiste), building permit issuance and management
- [Newsroom AI](https://www.nws.ai), personalised content delivery platform
- [Lambdahackers](https://lambdahackers.com), reddit-like groups for programmers
- [Breast Predict](https://breast.predict.nhs.uk/), predicting survival after adjuvant treatment for breast cancer
- [Prostate Predict](https://prostate.predict.nhs.uk/), prognostic model for men newly diagnosed with non-metastatic prostate cancer
- [Wobaka](https://wobaka.com), CRM system
- [Icebreaker](https://icebreaker.video/), online events
- [Carrot / OpenCompany](https://github.com/open-company/open-company-web), company updates
- [UXBOX](https://uxbox.io/), the open-source solution for design and prototyping

## Using Rum

Add to project.clj: `[rum "0.11.4"]`

### API Docs & Articles

[![cljdoc badge](https://cljdoc.org/badge/rum/rum)](https://cljdoc.org/d/rum/rum/CURRENT)

### Defining a component

Use `rum.core/defc` (short for “define component”) to define a function that returns component markup:

```clojure
(require [rum.core :as rum])

(rum/defc label [text]
  [:div {:class "label"} text])
```

Rum uses Hiccup-like syntax for defining markup:

```clojure
[<tag-n-selector> <attrs>? <children>*]
```

`<tag-n-selector>` defines a tag, its id and classes:

```clojure
  :span
  :span#id
  :span.class
  :span#id.class
  :span.class.class2
```

By default, if you omit the tag, `div` is assumed:

```
  :#id    === :div#id
  :.class === :div.class
```

`<attrs>` is an optional map of attributes:

- Use kebab-case keywords for attributes (e.g. `:allow-full-screen` for `allowFullScreen`)
- You can include `:id` and `:class` there as well
- `:class` can be a string or a sequence of strings
- `:style`, if needed, must be a map with kebab-case keywords
- event handlers should be arity-one functions

```clojure
[:input { :type      "text"
          :allow-full-screen true
          :id        "comment"
          :class     ["input_active" "input_error"]
          :style     { :background-color "#EEE"
                       :margin-left      42 }
          :on-change (fn [e]
                       (js/alert (.. e -target -value))) }]
```

`<children>` is a zero, one or many elements (strings or nested tags) with the same syntax:

```clojure
  [:div {} "Text"]         ;; tag, attrs, nested text
  [:div {} [:span]]        ;; tag, attrs, nested tag
  [:div "Text"]            ;; omitted attrs
  [:div "A" [:em "B"] "C"] ;; 3 children, mix of text and tags
```

Children can include lists or sequences which will be flattened:

```clojure
  [:div (list [:i "A"] [:b "B"])] === [:div [:i "A"] [:b "B"]]
```

By default all text nodes are escaped. To embed an unescaped string into a tag, add the `:dangerouslySetInnerHTML` attribute and omit children:

```clojure
  [:div { :dangerouslySetInnerHTML {:__html "<span></span>"}}]
```

### Rendering component

Given this code:

```clojure
(require [rum.core :as rum])

(rum/defc repeat-label [n text]
  [:div (replicate n [:.label text])])
```

First, we need to create a component instance by calling its function:

```
(repeat-label 5 "abc")
```

Then we need to pass that instance to `(rum.core/mount comp dom-node)`:

```clojure
(rum/mount (repeat-label 5 "abc") js/document.body)
```

And we will get this result:

```html
  <body>
    <div>
      <div class="label">abc</div>
      <div class="label">abc</div>
      <div class="label">abc</div>
      <div class="label">abc</div>
      <div class="label">abc</div>
    </div>
  </body>
```

Usually, `mount` is used just once in an app lifecycle to mount the top of your component tree to a page. After that, for a dynamic applications, you should either _update_ your components or rely on them to update themselves.

### Updating components manually

The simplest way to update your app is to mount it again:

```clojure
(rum/defc timer []
  [:div (.toISOString (js/Date.))])

(rum/mount (timer) js/document.body)

(js/setInterval
  #(rum/mount (timer) js/document.body)
  1000)
```

### Reactive components

Rum offers mixins as a way to hook into a component’s lifecycle and extend its capabilities or change its behaviour.

One very common use-case is for a component to update when some reference changes. Rum has a `rum.core/reactive` mixin just for that:

```clojure
(def count (atom 0))

(rum/defc counter < rum/reactive []
  [:div { :on-click (fn [_] (swap! count inc)) }
    "Clicks: " (rum/react count)])

(rum/mount (counter) js/document.body)
```

Two things are happening here:

1.  We’re adding the `rum.core/reactive` mixin to the component.
2.  We’re using `rum.core/react` instead of `deref` in the component body.

This will set up a watch on the `count` atom and will automatically call `rum.core/request-render` on the component each time the atom changes.

### Component’s local state

Sometimes you need to keep track of some mutable data just inside a component and nowhere else. Rum provides the `rum.core/local` mixin. It’s a little trickier to use, so hold on:

1.  Each component in Rum has internal state associated with it, normally used by mixins and Rum internals.
2.  `rum.core/local` creates a mixin that will put an atom into the component’s state.
3.  `rum.core/defcs` is used instead of `rum.core/defc`. It allows you to get hold of the components’s state in the render function (it will be passed as a first argument).
4.  You can then extract that atom from the component’s state and `deref`/`swap!`/`reset!` it as usual.
5.  Any change to the atom will force the component to update.

In practice, it’s quite convenient to use:

```clojure
(rum/defcs stateful < (rum/local 0 ::key)
  [state label]
  (let [local-atom (::key state)]
    [:div { :on-click (fn [_] (swap! local-atom inc)) }
      label ": " @local-atom]))

(rum/mount (stateful "Click count") js/document.body)
```

### Optimizing with shouldComponentUpdate

If your component accepts only immutable data structures as arguments, it may be a good idea to add the `rum.core/static` mixin:

```clojure
(rum/defc label < rum/static [n text]
  [:.label (replicate n text)])
```

`rum.core/static` will check if the arguments of a component’s constructor have changed (using Clojure’s `-equiv` semantic), and if they are the same, avoid re-rendering.

```clojure
(rum/mount (label 1 "abc") body)
(rum/mount (label 1 "abc") body) ;; render won’t be called
(rum/mount (label 1 "xyz") body) ;; this will cause a re-render
(rum/mount (label 1 "xyz") body) ;; this won’t
```

Note that this is not enabled by default because a) comparisons can be expensive, and b) things will go wrong if you pass a mutable reference as an argument.

### Writing your own mixin

Many applications have very specific requirements and custom optimization opportunities, so odds are you’ll be writing your own mixins.

Let’s see what a Rum component really is. Each Rum component has:

- A render function
- One or more mixins
- An internal state map
- A corresponding React component

For example, if we have this component defined:

```clojure
(rum/defc input [label value]
  [:label label ": "
    [:input { :value value }]])

(input "Your name" "")
```

It will have the following state:

```clojure
{ :rum/args ["Your name" ""]
  :rum/react-component <react-component> }
```

You can read the internal state by using the `rum.core/defcs` (short for “define component [and pass] state”) macro instead of `rum.core/defc`. It will pass `state` to the render function as the first argument:

```clojure
(rum/defcs label [state label value]
  [:div "My args:" (pr-str (:rum/args state))])

(label "A" 3) ;; => <div>My args: ["A" 3]</div>
```

The internal state cannot be directly manipulated, except at certain stages of a component’s lifecycle. Mixins are functions that are invoked at these stages to give you and opportunity to modify the state and/or do side effects to the world.

The following mixin will record the component’s mount time:

```clojure
(rum/defcs time-label
  < { :will-mount (fn [state]
                    (assoc state ::time (js/Date.))) }
  [state label]
  [:div label ": " (str (::time state))])
```

As you can see, `:will-mount` is a function from `state` to `state`. It gives you a chance to populate, clean or modify state map the moment before the component has been mounted.

Another useful thing you can do in a mixin is to decide when to update a component. If you can get ahold of React component (notice that that’s different from Rum component, unfortunately; sorry), you can call `rum.core/request-render` to schedule this component’s update at next frame (Rum uses `requestAnimationFrame` to batch and debounce component update calls). To get React component, just look up `:rum/react-component` key in a state.

This mixin will update a component each second:

```clojure
(def periodic-update-mixin
  { :did-mount    (fn [state]
                    (let [comp      (:rum/react-component state)
                          callback #(rum/request-render comp)
                          interval  (js/setInterval callback 1000)]
                       (assoc state ::interval interval)))
    :will-unmount (fn [state]
                    (js/clearInterval (::interval state))
                    (dissoc state ::interval)) })

(rum/defc timer < periodic-update-mixin []
  [:div (.toISOString (js/Date.))])

(rum/mount (timer) js/document.body)
```

Here’s a full list of callbacks you can define in a mixin:

```clojure
{ :init                 ;; state, props     ⇒ state
  :will-mount           ;; state            ⇒ state
  :before-render        ;; state            ⇒ state
  :wrap-render          ;; render-fn        ⇒ render-fn
  :render               ;; state            ⇒ [pseudo-dom state]
  :did-catch            ;; state, err, info ⇒ state
  :did-mount            ;; state            ⇒ state
  :after-render         ;; state            ⇒ state
  :did-remount          ;; old-state, state ⇒ state
  :should-update        ;; old-state, state ⇒ boolean
  :will-update          ;; state            ⇒ state
  :did-update           ;; state            ⇒ state
  :will-unmount }       ;; state            ⇒ state
```

Each component can have any number of mixins:

```clojure
(rum/defcs component
  < rum/static
    rum/reactive
    (rum/local 0 ::count)
    (rum/local "" ::text)
  [state label]
  (let [count-atom (::count state)
        text-atom  (::text state)]
    [:div])
```

One gotcha: don’t forget to return `state` from the mixin functions. If you’re using them for side-effects only, just return an unmodified `state`.

### Working with atoms

Since Rum relies a lot at components being able to efficiently update themselves in reaction to events, it includes two facilities to build architectures around Atoms and watchers.

#### Cursors

If you have a complex state and need a component to interact with only a part of it, create a cursor using `(rum.core/cursor-in ref path)`. Given atom with deep nested value and path inside it, `cursor-in` will create an atom-like structure that can be used separately from main atom, but will sync changes both ways:

```clojure
(def state (atom { :color "#cc3333"
                   :user { :name "Ivan" } }))

(def user-name (rum/cursor-in state [:user :name]))

@user-name ;; => "Ivan"

(reset! user-name "Oleg") ;; => "Oleg"

@state ;; => { :color "#cc3333"
       ;;      :user  { :name "Oleg" } }
```

Cursors implement `IAtom` and `IWatchable` and interface-wise are drop-in replacement for regular atoms. They work well with `rum/reactive` and `rum/react` too.

#### Derived atoms

Use derived atoms to create “chains” and acyclic graphs of dependent atoms. `derived-atom` will:

- Take N “source” refs
- Set up a watch on each of them
- Create “sink” atom
- When any of source refs changes:
  - re-run function `f`, passing N dereferenced values of source refs
  - `reset!` result of `f` to the sink atom
- return sink atom

```clojure
  (def *a (atom 0))
  (def *b (atom 1))
  (def *x (derived-atom [*a *b] ::key
            (fn [a b]
              (str a \":\" b))))
  (type *x) ;; => clojure.lang.Atom
  @*x     ;; => 0:1
  (swap! *a inc)
  @*x     ;; => 1:1
  (reset! *b 7)
  @*x     ;; => 1:7
```

Derived atoms are like cursors, but can “depend on” multiple references and won’t sync changes back to the source if you try to update derived atom (don’t).

### Interop with React

#### Native React component

You can access the raw React component by reading the state’s `:rum/react-component` attribute:

```clojure
{ :did-mount (fn [state]
               (let [comp     (:rum/react-component state)
                     dom-node (js/ReactDOM.findDOMNode comp)]
                 (set! (.-width (.-style dom-node)) "100px"))
               state) }
```

#### React keys and refs

There’re three ways to specify React keys:

1.  If you need a key on Sablono tag, put it into attributes: `[:div { :key "x" }]`
2.  If you need a key on Rum component, use `with-key`:

```clojure
(rum/defc my-component [str]
  ...)

(rum/with-key (my-component "args") "x")
```

3.  or, you can specify `:key-fn` in a mixin to calculate key based on args at component creation time:

```clojure
(rum/defc my-component
  < { :key-fn (fn [x y z]
                (str x "-" y "-" z)) }
  [x y z]
  ...)

(my-component 1 2 3) ;; => key == "1-2-3"
```

`:key-fn` must accept same arguments your render function does.

Refs work the same way as options 1 and 2 for keys work:

1.  `[:div { :ref "x" }]`
2.  `(rum/with-ref (my-component) "x")`

#### Accessing DOM 

⚠️ These helpers are deprecated since usage of string refs has been deprecated in React itself. Instead use a callback that receives a DOM node.
```clojure
[:div {:ref (fn [node] ...)}]
```

There’re couple of helpers that will, given state map, find stuff in it for you:

```clojure
(rum/dom-node state)     ;; => top-level DOM node
(rum/ref      state "x") ;; => ref-ed React component
(rum/ref-node state "x") ;; => top-level DOM node of ref-ed React component
```

#### Custom class properties

To define arbitrary properties and methods on a component class, specify a `:class-properties` map in a mixin:

```clojure
(rum/defc comp
  < { :class-properties { ... } }
  [:div]))
```

To define static properties on a component class, specify a `:static-properties` map in a mixin:

```clojure
(rum/defc comp
  < { :static-properties { ... } }
  [:div]))
```

#### React context

To define child context

1.  Add dependency `[cljsjs/prop-types "15.5.10-1"]`
2.  `(require [cljsjs.prop-types])`
3.  Specify a `:child-context` function taking state and returning context map in a mixin:

```clojure
(rum/defc theme
  < { :child-context
      (fn [state]
        (let [[color] (:rum/args state)]
          { :color color }))
      :static-properties
      { :childContextTypes {:color js/PropTypes.string} } }
  [color child]
  child)
```

#### React Hooks

There are Rum wrappers for the various React hooks. See doc strings for examples, and the
[the React hooks reference]((https://reactjs.org/docs/hooks-reference.html)) for more details.

```clojure
;; Takes initial value or value returning fn and returns a tuple of [value set-value!],
;; where `value` is current state value and `set-value!` is a function that schedules re-render.
(rum/use-state [value-or-fn])

;; Takes reducing function and initial state value.
;; Returns a tuple of [value dispatch!], where `value` is current state value and `dispatch` is a function that schedules re-render.
(rum/use-reducer [reducer-fn initial-value])

;; Takes setup-fn that executes either on the first render or after every update.
;; The function may return cleanup-fn to cleanup the effect, either before unmount or before every next update.
;; Calling behavior is controlled by deps argument.
(rum/use-effect! [setup-fn])
(rum/use-effect! [setup-fn deps])

;; Takes callback function and returns memoized variant, memoization is done based on provided deps collection.
(rum/use-callback [callback])
(rum/use-callback [callback deps])

;; Takes a function, memoizes it based on provided deps collection and executes immediately returning a result.
(rum/use-memo [f])
(rum/use-memo [f deps])

;; Takes a value and puts it into a mutable container which is persisted for the full lifetime of the component.
(rum/use-ref [initial-value])
```

### Server-side rendering

If used from clj/cljc, Rum works as a traditional template engine à la Hiccup:

1.  Rum’s `project.clj` dependency becomes `[rum "0.11.4" :exclusions [cljsjs/react cljsjs/react-dom sablono]`
2.  Import `rum.core` as usual.
3.  Define components using `rum/defc` or other macros as usual.
4.  Instead of mounting, call `rum/render-html` to render into a string.
5.  Generate the HTML page using that string.
6.  On the client side, mount (but using `rum/hydrate`) _the same_ component over the node where you rendered your server-side component.

```clojure
(require '[rum.core :as rum])

(rum/defc my-comp [s]
  [:div s])

;; on a server
(rum/render-html (my-comp "hello"))
;; => "<div data-reactroot=\"\">hello</div>"

;; on a client
(rum/hydrate (my-comp "hello") js/document.body)
```

Use `rum/render-static-markup` if you’re not planning to connect your page with React later:

```clojure
(rum/render-static-markup (my-comp "hello")) ;; => <div>hello</div>
```

Rum server-side rendering does not use React or Sablono, it runs completely in JVM, without involving JavaScript at any stage.

As of `[rum "0.8.3"]` and `[hiccup "1.0.5"]`, Rum is ~3× times faster than Hiccup.

Server-side components do not have full lifecycle support, but `:init` and `:will-mount` from mixins would be called at the component’s construction time.

## Support

- Join [#rum on Clojurians Slack](https://clojurians.slack.com/messages/C08H80CUR/) (grab invite [here](http://clojurians.net/))
- Check out [our wiki](https://github.com/tonsky/rum/wiki)

### Talks

- [Rum workshop](https://www.youtube.com/watch?v=RqHnxkU9TZE) at Cognician, by me
- [Norbert Wójtowicz talk at Lambda Days 2015](https://vimeo.com/122316380) where he explains benefits of web development with ClojureScript and React, and how Rum emulates all main ClojureScript frameworks
- [Hangout about Rum](https://www.youtube.com/watch?v=8evDKjD5vt4) (in Russian)

### App templates

- [Tenzing](https://github.com/martinklepsch/tenzing)

### Libraries

- [Reforms](http://bilus.github.io/reforms/), Bootstrap 3 forms
- [rum-mdl](http://ajchemist.github.io/rum-mdl/), Material design lite components
- [derivatives](https://github.com/martinklepsch/derivatives), creates chains of derived values from an atom
- [citrus](https://github.com/roman01la/citrus), state coordination framework (previously known as scrum)
- [Antizer](https://github.com/priornix/antizer) Ant Design component library

### Examples

- In this repo see [examples/rum/examples/](examples/rum/examples/). [Live version](http://tonsky.me/rum/)
- [DataScript Chat app](https://github.com/tonsky/datascript-chat)
- [DataScript ToDo app](https://github.com/tonsky/datascript-todo)
- [DataScript Menu app](https://github.com/tonsky/datascript-menu)

## Acknowledgements

Rum was build on inspiration from [Quiescent](https://github.com/levand/quiescent), [Om](https://github.com/swannodette/om) and [Reagent](https://github.com/reagent-project/reagent).

All heavy lifting done by [React](http://facebook.github.io/react/), [Ŝablono](https://github.com/r0man/sablono) and [ClojureScript](https://github.com/clojure/clojurescript).

## License

Copyright © 2014 Nikita Prokopov

Licensed under Eclipse Public License (see [LICENSE](LICENSE)).

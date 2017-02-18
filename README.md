<p align="center"><img src="https://dl.dropboxusercontent.com/u/561580/imgs/rum_logo.svg" style="height: 400px;"></p>

Rum is a client/server library for HTML UI. In ClojureScript, it works as React wrapper, in Clojure, it is a static HTML generator.

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
- [modnaKasta](https://modnaKasta.ua), online shopping
- [ChildrensHeartSurgery.info](http://childrensheartsurgery.info), heart surgery statistics
- [Mighty Hype](http://mightyhype.com/), cinema platform (server-side rendering)
- [TourneyBot](http://houstonindoor.com/2016), frisbee tournament app

## Using Rum

Add to project.clj: `[rum "0.10.8"]`

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
  [:div (repeat n [:.label text])])
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

1. We’re adding the `rum.core/reactive` mixin to the component.
2. We’re using `rum.core/react` instead of `deref` in the component body.

This will set up a watch on the `count` atom and will automatically call `rum.core/request-render` on the component each time the atom changes.


### Component’s local state

Sometimes you need to keep track of some mutable data just inside a component and nowhere else. Rum provides the `rum.core/local` mixin. It’s a little trickier to use, so hold on:

1. Each component in Rum has internal state associated with it, normally used by mixins and Rum internals.
2. `rum.core/local` creates a mixin that will put an atom into the component’s state.
3. `rum.core/defcs` is used instead of `rum.core/defc`. It allows you to get hold of the components’s state in the render function (it will be passed as a first argument).
4. You can then extract that atom from the component’s state and `deref`/`swap!`/`reset!` it as usual.
5. Any change to the atom will force the component to update.

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
  [:.label (repeat n text)])
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
(rum/defcs time-label < { :did-mount (fn [state]
                                       (assoc state ::time (js/Date.))) }
  [state label]
  [:div label ": " (::time state)])
```

As you can see, `:did-mount` is a function from `state` to `state`. It gives you a chance to populate, clean or modify state map after the component has been mounted.

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
(rum/defcs component < rum/static 
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

**Cursors**

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

**Derived atoms**

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

**Native React component**

You can access the raw React component by reading the state’s `:rum/react-component` attribute:

```clojure
{ :did-mount (fn [state]
               (let [comp     (:rum/react-component state)
                     dom-node (js/ReactDOM.findDOMNode comp)]
                 (set! (.-width (.-style dom-node)) "100px"))
               state) }
```

**React keys and refs**

There’re three ways to specify React keys:

1. If you need a key on Sablono tag, put it into attributes: `[:div { :key "x" }]`
2. If you need a key on Rum component, use `with-key`:

  ```clojure
  (rum/defc my-component [str]
    ...)

  (rum/with-key (my-component "args") "x")
  ```
3. or, you can specify `:key-fn` in a mixin to calculate key based on args at component creation time:

  ```clojure
  (rum/defc my-component < { :key-fn (fn [x y z]
                                       (str x "-" y "-" z)) }
    [x y z]
    ...)

  (my-component 1 2 3) ;; => key == "1-2-3"
  ```

`:key-fn` must accept same arguments your render function does.

Refs work the same way as options 1 and 2 for keys work:

1. `[:div { :ref "x" }]`
2. `(rum/with-ref (my-component) "x")`


**Accessing DOM**

There’re couple of helpers that will, given state map, find stuff in it for you:

```clojure
(rum/dom-node state)     ;; => top-level DOM node
(rum/ref      state "x") ;; => ref-ed React component
(rum/ref-node state "x") ;; => top-level DOM node of ref-ed React component
```

**Custom class properties**

To define arbitrary properties and methods on a component class, specify a `:class-properties` map in a mixin:

```clojure
(rum/defc comp < { :class-properties { ... } }
  [:div]))
```

**React context**

To define child context, specify a `:child-context` function taking state and returning context map in a mixin:

```clojure
(rum/defc theme < { :child-context
                    (fn [state]
                      (let [[color] (:rum/args state)]
                        { :color color }))
                    :class-properties
                    { :childContextTypes {:color js/React.PropTypes.string} } }
  [color child]
  child)
```

### Server-side rendering

If used from clj/cljc, Rum works as a traditional template engine à la Hiccup:

1. Import `rum.core` as usual.
2. Define components using `rum/defc` or other macros as usual.
3. Instead of mounting, call `rum/render-html` to render into a string.
4. Generate the HTML page using that string.
5. On the client side, mount _the same_ component over the node where you rendered your server-side component.

```clojure
(require '[rum.core :as rum])

(rum/defc my-comp [s]
  [:div s])

;; on a server
(rum/render-html (my-comp "hello"))
;; => "<div data-reactroot=\"\" data-reactid=\"1\" data-react-checksum=\"-857140882\">hello</div>"

;; on a client
(rum/mount (my-comp "hello") js/document.body)
```

Use `rum/render-static-markup` if you’re not planning to connect your page with React later:

```clojure
(rum/render-static-markup (my-comp "hello")) ;; => <div>hello</div>
```

Rum server-side rendering does not use React or Sablono, it runs completely in JVM, without involving JavaScript at any stage.

As of `[rum "0.8.3"]` and `[hiccup "1.0.5"]`, Rum is ~3× times faster than Hiccup.

Server-side components do not have full lifecycle support, but `:init` and `:will-mount` from mixins would be called at the component’s construction time.

## Resources

- Ask for help on [Gitter chat](https://gitter.im/tonsky/rum)
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
- [scrum](https://github.com/roman01la/scrum), state coordination framework for Rum


### Examples

- In this repo see [examples/rum/examples/](examples/rum/examples/). [Live version](http://tonsky.me/rum/)
- [DataScript Chat app](https://github.com/tonsky/datascript-chat)
- [DataScript ToDo app](https://github.com/tonsky/datascript-todo)
- [DataScript Menu app](https://github.com/tonsky/datascript-menu)

## Changes

### 0.10.8

- React 15.4.2-0, Sablono 0.7.7
- Render boolean `aria-*` values as strings (thx [r0man](https://github.com/r0man), PR #114) 
- Escape attributes during server-side rendering (thx [Alexander Solovyov](https://github.com/piranha), PR #115)

### 0.10.7

- Fixed server-side rendering discrepancy (issue #99)
- Sablono 0.7.5, React 15.3.1-0

### 0.10.6

- Sablono 0.7.4 [fixes the issue](https://github.com/r0man/sablono/pull/129) with controlling components refusing to change value if non-string value was used
- React 15.3.0-0
- Throw error when `<` is misplaced in `defc` (thx [Martin Klepsch](https://github.com/martinklepsch), issue #88, PR #90)

### 0.10.5

- Sablono 0.7.3 fixes the issue when IE lost keystrokes in controlled inputs/textarea (#86)
- React 15.2.1-1
- Warn when `rum.core/react` is used without `rum.core/reactive` (thx [Martin Klepsch](https://github.com/martinklepsch), issue #82, PR #87)

### 0.10.4

- Ability to use `:pre` and `:post` checks in `rum.core/defc` (thx [Martin Klepsch](https://github.com/martinklepsch), PR #81)

### 0.10.3

- Fixed regression of `displayName` in 0.10.0
- Bumped React to 15.2.0

### 0.10.2

- Fixed a bug when `:before-render` and `:will-update` weren’t called on subsequent renders

### 0.10.1

- Made `rum.core/state` public again
- `:before-render` should be called on server-side rendering too (thx [Alexander Solovyov](https://github.com/piranha), PR #79)

### 0.10.0

A big cleanup/optmization/goodies release with a lot breaking changes. Read carefully!

- [ BREAKING ] `cursor` got renamed to `cursor-in`. New `cursor` method added that takes single key (as everywhere in Clojure)
- [ BREAKING ] `rum/mount` returns `nil` (because you [shouldn’t rely on return value of ReactDOM.render](https://github.com/facebook/react/issues/4936))
- [ BREAKING ] `:transfer-state` is gone. All of component’s state is now transferred by default. If you still need to do something fancy on `componentWillReceiveProps`, new callback is called `:did-remount` callback
- [ BREAKING ] removed `cursored` and `cursored-watch` mixins. They felt too unnatural to use
- [ BREAKING ] removed `rum/with-props` (deprecated since 0.3.0). Use `rum/with-key` and `rum/with-ref` instead
- [ BREAKING ] server-side rendering no longer calls `:did-mount` (obviously, that was a mistake)
- [ BREAKING ] `:rum/id` is gone. If you need an unique id per component, allocate one in `:init` as store it in state under namespaced key

When upgrading to 0.10.0, check this migration checklist:

- Change all `rum/cursor` calls to `rum/cursor-in`
- Find all `:transfer-state` mixins.
  - If the only thing they were doing is something like `(fn [old new] (assoc new ::key (::key old)))`, just delete them.
  - If not, rename to `:did-remount`
- Check if you were using `rum/mount` return value. If yes, find another way to obtain component (e.g. via `ref`, `defcc` etc)
- Replace `rum/with-props` with `rum/with-key`, `rum/with-ref` or `:key-fn`
- Check that you weren’t relying on `:did-mount` in server-side rendering

Now for the good stuff:

- Cursors now support metadata, `alter-meta!` etc
- Cursors can be used from Clojure
- Added `:key-fn` to mixins. That function will be called before element creation, with same arguments as render fn, and its return value will be used as a key on that element
- Mixins can specify `:before-render` (triggered at `componentWillMount` and `componentWillUpdate`) and `:after-render` (`componentDidMount` and `componentDidUpdate`) callback
- Added `rum/ref` and `rum/ref-node` helpers, returning backing component and DOM node
- Some client-side API functions added to server version (`dom-node`, `unmount`, `request-render` etc). Their implementation just throws an exception. This is to help you write less conditional directives in e.g. `:did-mount` or `:will-unmount` mixins. They will never be called, but won’t stop code from compiling either.

And couple of optimizations:

- Rum now makes use of staless components (nothing for you to do, if your component is defined via `defc` with no mixins, it’ll be automatically compiled to stateless component)
- Rum will use React’s batched updates to perform rendering on `requestAnimationFrame` in a single chunk
- Streamlined internals of component construction, removed `render->mixin`, `args->state`, `element` and `ctor->class`


### 0.9.1

- Added `rum.core/derived-atom`, a function that let you build reactive chains and directed acyclic graphs of dependent atoms. E.g. you want `*c` to always contain a value of `*a` plus a value of `*b` and update whenever any of them changes
- Added `rum.core/dom-node` helper that takes state and finds corresponding top DOM node of a component. Can be called in mixins after initial render only
- Fixed compatibility of `with-key` on nil-returning component in server rendering (thx [Alexander Solovyov](https://github.com/piranha), PR #73)

### 0.9.0

- Better support for server-side rendering of SVG
- [ BREAKING ] Rum used to support multiple ways to specify attributes. You would expect that both `:allow-full-screen`, `:allowFullScreen` and `"allowFullScreen"` would be normalized to `allowfullscreen`. As a result, you have to face three problems: 
  - how do I decide which variant to use?
  - how do I ensure consistency accross my team and our codebase?
  - find & replace become harder

Starting with 0.9.0, Rum will adopt “There’s Only One Way To Do It” policy. All attributes MUST be specified as kebab-cased keywords:

| Attribute | What to use | What not to use |
| --------- | ----------- | --------------- |
| class     | `:class`    | ~~`:class-name`~~ ~~`:className`~~ |
| for       | `:for`      | ~~`:html-for`~~ ~~`:htmlFor`~~ |
| unescaped innerHTML | `:dangerouslySetInnerHTML { :__html { "..." }}` | |
| uncontrolled value | `:default-value` | ~~`:defaultValue`~~ |
| uncontrolled checked | `:default-checked` | ~~`:defaultChecked`~~ |
| itemid, classid | `:item-id`, `:class-id` | ~~`:itemID`~~ ~~`:itemId`~~ ~~`:itemid`~~|
| xml:lang etc | `:xml-lang` | ~~`:xml/lang`~~ ~~`:xmlLang`~~ ~~`"xml:lang"`~~ |
| xlink:href etc | `:xlink-href` | ~~`:xlink/href`~~ ~~`:xlinkHref`~~ ~~`"xlink:href"`~~ |
| xmlns | not supported |  |

To migrate to 0.9.0 from earlier versions, just do search-and-replace for non-standard variants and replace them with recommended ones.

### 0.8.4

- Improved server-side rendering for inputs (issue #67 & beyond)
- Compatible server-side rendering of components that return nil (issue #64)
- Upgraded React to 15.1.0

### 0.8.3

- `rum/render-static-markup` call for pure HTML templating. Use it if you’re not planning to connect your page with React later
- `rum/def*` macros now correctly retain metadata that already exists on a symbol (thx [aJchemist](https://github.com/aJchemist), PR #62)

### 0.8.2

- Add `rum.core/unmount` function (thx [emnh](https://github.com/emnh), issue #61)

### 0.8.1

- Retain `:arglists` metadata on vars defined by `rum/def*` macros (thx [aJchemist](https://github.com/aJchemist), PR #60)

### 0.8.0

- Migrated to React 15.0.1
- Optimized server-side rendering (~4× faster than Rum 0.7.0, ~2-3× faster than Hiccup 1.0.5)

### 0.7.0

- Server-side rendering via `rum/render-html` (thx [Alexander Solovyov](https://github.com/piranha))

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

All heavy lifting done by [React](http://facebook.github.io/react/), [Ŝablono](https://github.com/r0man/sablono) and [ClojureScript](https://github.com/clojure/clojurescript).

## License

Copyright © 2014–2016 Nikita Prokopov

Licensed under Eclipse Public License (see [LICENSE](LICENSE)).

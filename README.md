<p align="center"><img src="https://dl.dropboxusercontent.com/u/561580/imgs/rum_logo.svg" style="height: 400px;"></p>

Rum is a client/server library for HTML UI. In ClojureScript, it works as React wrapper, in Clojure, it is a static HTML generator.

### Principles

**Simple semantics**: Rum is arguably smaller, simpler and more straightforward than React itself.

**Decomplected**: Rum is a library, not a framework. Use only the parts you need, throw away or replace what you don’t need, combine different approaches in a single app, or even combine Rum with other frameworks.

**No enforced state model**: Unlike Om, Reagent or Quiescent, Rum does not dictate where to keep your state. Instead, it works well with any storage: persistent data structures, atoms, DataScript, JavaScript objects, localStorage or any custom solution you can think of.

**Extensible**: the API is stable and explicitly defined, including the API between Rum internals. It lets you build custom behaviours that change components in significant ways.

**Minimal codebase**: You can become a Rum expert just by reading its source code (~700 lines).

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
- [TourneyBot](http://houstonindoor.com/2016), frisbee tournament app

## Using Rum

Add to project.clj: `[rum "0.9.1"]`

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
(rum/mount (repeat-label 5 "abc") js/document.body)

(js/setTimeout
  #(rum/mount (repeat-label 3 "xyz") js/document.body)
  1000)
```

A better way is to use `(rum.core/request-render react-component)` which will schedule an update at the next animation frame. It will also throttle duplicate update calls if you happen to call `request-render` more than once:

```clojure
(rum/defc my-app []
  [:div (rand)])

(let [react-comp (rum/mount (my-app) js/document.body)]
  (js/setTimeout #(rum/request-render react-comp) 1000))
```

Note that `request-render` accepts a React component, not a Rum component. One way to get it is to save the return value of `mount`, but we’ll look at other ways to do this in the “Writing your own mixin” section.

Also note that `request-render` does not let you change component arguments. It expects the component to be responsible for getting its own state when the render function is called.

This is already enough to build a simple click counter:

```clojure
(def count (atom 0))

(rum/defc counter []
  [:div { :on-click (fn [_] (swap! count inc)) }
    "Clicks: " @count])
    
(let [react-comp (rum/mount (counter) js/document.body)]
  (add-watch count ::render
    (fn [_ _ _ _]
      (rum/request-render react-comp))))
```

### Reactive components

Rum offers mixins as a way to hook into a component's lifecycle and extend its capabilities or change its behaviour.

One very common use-case is for a component to update when some reference changes. Rum has a `rum.core/reactive` mixin just for that:

```clojure
(def count (atom 0))

(rum/defc counter < rum/reactive []
  [:div { :on-click (fn [_] (swap! count inc)) }
    "Clicks: " (rum/react count)])
    
(rum/mount (counter) js/document.body)
```

Two things are happening here:

1. We’ve added the `rum.core/reactive` mixin to the component.
2. We’ve used `rum.core/react` instead of `deref` in the component body.

This will set up a watch on the `count` atom and will automatically call `rum.core/request-render` on the component each time the atom changes.

If you have a complex state and need a component to interact with only some part of it, create a _derived cursor_ using `(rum.core/cursor ref path)`:

```clojure
(def state (atom { :color "#cc3333"
                   :user { :name "Ivan" } }))

(def user-name (rum/cursor state [:user :name]))

@user-name ;; => "Ivan"

(reset! user-name "Oleg") ;; => "Oleg"

@state ;; => { :color "#cc3333"
       ;;      :user  { :name "Oleg" } }
```

Cursors implement `IAtom` and `IWatchable` and interface-wise are drop-in replacement for regular atoms. They work well with `rum/reactive` and `rum/react` too.

### Component’s local state

Sometimes you need to keep track of some mutable data just inside a component and nowhere else. Rum provides the `rum.core/local` mixin. It’s a little trickier to use, so hold on:

1. Each component in Rum has internal state associated with it, normally used by mixins and Rum internals.
2. `rum.core/local` creates a mixin that will put an atom into the component’s state.
3. `rum.core/defcs` is used instead of `rum.core/defc`. It allows you to get hold of the components’s state in the render function (it will be passed as a first argument).
4. You can then extract that atom from the component's state and `deref`/`swap!`/`reset!` it as usual.
5. Any change to the atom will force the component to update.

In practice, it’s quite convenient to use:

```clojure
(rum/defcs stateful < (rum/local 0) [state label]
  (let [local-atom (:rum/local state)]
    [:div { :on-click (fn [_] (swap! local-atom inc)) }
      label ": " @local-atom]))
      
(rum/mount (stateful "Click count") js/document.body)
```

By default the atom will be associated to the `:rum/local` key in the component's state. You can change this by specifying a second argument to `rum.core/local`, e.g.

```clojure
(rum/defcs input < (rum/local "" ::text)
  [state]
  (let [text-atom (::text state)]
    [:input { :type  "text"
              :value @text-atom
              :on-change (fn [e]
                           (reset! text-atom (.. e -target -value))) }]))
```

### Optimizing with shouldComponentUpdate

If your component accepts only immutable data structures as arguments, it may be a good idea to add the `rum.core/static` mixin:

```clojure
(rum/defc label < rum/static [n text]
  [:.label (repeat n text)])
```

`rum.core/static` will check if the arguments of a component's constructor have changed (using Clojure’s `-equiv` semantic), and if they are the same, avoid re-rendering.

```clojure
(rum/mount (label 1 "abc") body)
(rum/mount (label 1 "abc") body) ;; render won’t be called
(rum/mount (label 1 "xyz") body) ;; this will cause a re-render
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
{ :rum/id   <int>
  :rum/args ["Your name" ""]
  :rum/react-component <react-component> }
```

You can read the internal state by using the `rum.core/defcs` (short for “define component [and pass] state”) macro instead of `rum.core/defc`. It will pass `state` as the first argument:

```clojure
(rum/defcs label [state label value]
  [:div "My args:" (pr-str (:rum/args state))])
  
(label "A" 3) ;; => <div>My args: ["A" 3]</div>
```

The internal state cannot be directly manipulated, except at certain stages of a component's lifecycle. Mixins are functions that are invoked at these stages to modify the state and/or do side effects to the world.

The following mixin will record the component’s mount time:

```clojure
(rum/defcs time-label < { :did-mount (fn [state]
                                       (assoc state ::time (Date.))) }
  [state label]
  [:div label ": " (::time state)])
```

As you can see, `:did-mount` is a function from `state` to `state` and can populate, clean or modify it only after the component has been mounted.

This mixin will update a component each second:

```clojure
(def periodic-update-mixin
  { :did-mount      (fn [state]
                      (let [comp      (:rum/react-component state)
                            callback #(rum/request-render comp)
                            interval  (js/setInterval callback 1000)]
                         (assoc state ::interval interval)))
    :transfer-state (fn [old-state state]
                      (assoc state ::interval (::interval old-state)))
    :will-unmount   (fn [state]
                      (js/clearInterval (::interval state))
                      (dissoc state ::interval)) })

(rum/defc timer < periodic-update-mixin []
  [:div (.toISOString (js/Date.))])
  
(rum/mount (timer) js/document.body)
```

Two gotchas:

- Don’t forget to return `state` from the mixin functions. If you’re using them for side-effects only, just return an unmodified `state`.
- If you add something to the state through `:did-mount`/`:will-mount`, you must write a `:transfer-state` function to move that attribute from the old component instance to the new one.

Here’s a full list of callbacks you can define in a mixin:

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

### Interop with React

You can access the raw React component by reading the state's `:rum/react-component` attribute:

```clojure
{ :did-mount (fn [state]
               (let [comp     (:rum/react-component state)
                     dom-node (js/ReactDOM.findDOMNode comp)]
                 (set! (.-width (.-style dom-node)) "100px"))
               state) }
```

You can’t specify a React key from inside component, but you can do so when you create it:

```clojure
(rum/defc my-component [str]
  ...)
  
(rum/with-key (my-component "args") 77)
```

To define arbitrary properties and methods on a component class, specify a `:class-properties` map in a mixin:

```clojure
(rum/defc comp < { :class-properties { ... } }
  [:div]))
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
(rum/mount (my-comp "hello") (js/document.querySelector "[data-reactroot]))
```

Use `rum/render-static-markup` if you’re not planning to connect your page with React later:

```clojure
(rum/render-static-markup (my-comp "hello")) ;; => <div>hello</div>
```

Rum server-side rendering does not use React or Sablono, it runs completely in JVM, without involving JavaScript at any stage.

As of `[rum "0.8.3"]` and `[hiccup "1.0.5"]`, Rum is ~3× times faster than Hiccup.

Server-side components do not have full lifecycle support, but `:init` and `:will-mount` from mixins would be called at the component's construction time.

## Resources

- Ask for help on [Gitter chat](https://gitter.im/tonsky/rum)
- Check out [our wiki](https://github.com/tonsky/rum/wiki)

### Libraries

- [Reforms](http://bilus.github.io/reforms/), Bootstrap 3 forms
- [rum-mdl](http://ajchemist.github.io/rum-mdl/), Material design lite components

### Examples

- In this repo see [examples/rum/examples/](examples/rum/examples/). [Live version](http://tonsky.me/rum/)
- [DataScript Chat app](https://github.com/tonsky/datascript-chat)
- [DataScript ToDo app](https://github.com/tonsky/datascript-todo)
- [DataScript Menu app](https://github.com/tonsky/datascript-menu)

### Talks

- [Norbert Wójtowicz talk at Lambda Days 2015](https://vimeo.com/122316380) where he explains benefits of web development with ClojureScript and React, and how Rum emulates all main ClojureScript frameworks
- [Hangout about Rum](https://www.youtube.com/watch?v=8evDKjD5vt4) (in Russian)

## Changes

### WIP

- [ BREAKING ] `cursor` got renamed to `cursor-in`. New `cursor` method added that takes single key (as everywhere in Clojure)
- [ BREAKING ] `rum/mount` returns `nil` (because you [shouldn’t rely on return value of ReactDOM.render](https://github.com/facebook/react/issues/4936))
- cursors now support metadata, alter-meta! etc
- cursors can now be used from Clojure
- Rum now makes use of staless components (nothing for you to do, if your component doesn’t use any mixins, it’ll be automatically compiled to stateless component)
- [ BREAKING ] server-side rendering no longer calls `:did-mount` (obviously, that was a mistake)
- some client-side API functions added to server version (`dom-node`, `unmount`, `request-render` etc). Their implementation just throws an exception. This is to help you write less conditional directives in e.g. `:did-mount` or `:will-unmount` mixins. They will never be called, but won’t stop code from compiling either.

### 0.9.1

- Added `rum.core/derived-atom`, a function that let you build reactive chains and directed acyclic graphs of dependent atoms. E.g. you want `*c` to always contain a value of `*a` plus a value of `*b` and update whenever any of them changes. Do:

```clj
(def *a (atom 2))
(def *b (atom 3))
(def *c (rum.core/derived-atom [*a *b] ::c-sum (fn [a b] (+ a b))))
@*c ;; => 5
(swap! *a inc)
@*c ;; => 6
(swap! *b + 10)
@*c ;; => 16
```

- Added `rum.core/dom-node` helper that takes state and finds corresponding top DOM node of a component. Can be called in mixins after initial render only
- Fixed compatibility of `with-key` on nil-returning component in server rendering (thx [Alexander Solovyov](https://github.com/piranha), PR #73)

### 0.9.0

- Better support for server-side rendering of SVG
- [ BREAKING ] Rum used to support multiple ways to specify attributes. You would expect that both `:allow-full-screen`, `:allowFullScreen` and `"allowFullScreen"` would be normalized to `allowfullscreen`. As a result, you have to face three problems: 
  - how do I decide which variant to use?
  - how do I ensure consistency accross my team and our codebase?
  - find & replace become harder

Starting with 0.9.0, Rum will adopt “There's Only One Way To Do It” policy. All attributes MUST be specified as kebab-cased keywords:

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

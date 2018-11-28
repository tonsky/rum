## 0.11.3

- Docstrings for https://cljdoc.org/d/rum/rum

## 0.11.2

- Server-render on-* event handlers with string values

## 0.11.1

- Sablono or CLJS are excluded completely when using SSR (#83 #157)

## 0.11.0 (thx [Roman Liutikov](https://github.com/roman01la) & [Alexander Solovyov](https://github.com/piranha), PR #151)

- [ BREAKING ] `contextTypes` and `childContextTypes` should be specified through `:static-properties` instead of `:class-properties`
- React 16.2.0, Sablono 0.8.1
- Added `rum/portal` method
- Added `:did-catch` lifecycle callback
- Added `rum/hydrate` and updated SSR output to match React’s

## 0.10.8

- React 15.4.2-0, Sablono 0.7.7
- Render boolean `aria-*` values as strings (thx [r0man](https://github.com/r0man), PR #114) 
- Escape attributes during server-side rendering (thx [Alexander Solovyov](https://github.com/piranha), PR #115)

## 0.10.7

- Fixed server-side rendering discrepancy (issue #99)
- Sablono 0.7.5, React 15.3.1-0

## 0.10.6

- Sablono 0.7.4 [fixes the issue](https://github.com/r0man/sablono/pull/129) with controlling components refusing to change value if non-string value was used
- React 15.3.0-0
- Throw error when `<` is misplaced in `defc` (thx [Martin Klepsch](https://github.com/martinklepsch), issue #88, PR #90)

## 0.10.5

- Sablono 0.7.3 fixes the issue when IE lost keystrokes in controlled inputs/textarea (#86)
- React 15.2.1-1
- Warn when `rum.core/react` is used without `rum.core/reactive` (thx [Martin Klepsch](https://github.com/martinklepsch), issue #82, PR #87)

## 0.10.4

- Ability to use `:pre` and `:post` checks in `rum.core/defc` (thx [Martin Klepsch](https://github.com/martinklepsch), PR #81)

## 0.10.3

- Fixed regression of `displayName` in 0.10.0
- Bumped React to 15.2.0

## 0.10.2

- Fixed a bug when `:before-render` and `:will-update` weren’t called on subsequent renders

## 0.10.1

- Made `rum.core/state` public again
- `:before-render` should be called on server-side rendering too (thx [Alexander Solovyov](https://github.com/piranha), PR #79)

## 0.10.0

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


## 0.9.1

- Added `rum.core/derived-atom`, a function that let you build reactive chains and directed acyclic graphs of dependent atoms. E.g. you want `*c` to always contain a value of `*a` plus a value of `*b` and update whenever any of them changes
- Added `rum.core/dom-node` helper that takes state and finds corresponding top DOM node of a component. Can be called in mixins after initial render only
- Fixed compatibility of `with-key` on nil-returning component in server rendering (thx [Alexander Solovyov](https://github.com/piranha), PR #73)

## 0.9.0

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

## 0.8.4

- Improved server-side rendering for inputs (issue #67 & beyond)
- Compatible server-side rendering of components that return nil (issue #64)
- Upgraded React to 15.1.0

## 0.8.3

- `rum/render-static-markup` call for pure HTML templating. Use it if you’re not planning to connect your page with React later
- `rum/def*` macros now correctly retain metadata that already exists on a symbol (thx [aJchemist](https://github.com/aJchemist), PR #62)

## 0.8.2

- Add `rum.core/unmount` function (thx [emnh](https://github.com/emnh), issue #61)

## 0.8.1

- Retain `:arglists` metadata on vars defined by `rum/def*` macros (thx [aJchemist](https://github.com/aJchemist), PR #60)

## 0.8.0

- Migrated to React 15.0.1
- Optimized server-side rendering (~4× faster than Rum 0.7.0, ~2-3× faster than Hiccup 1.0.5)

## 0.7.0

- Server-side rendering via `rum/render-html` (thx [Alexander Solovyov](https://github.com/piranha))

## 0.6.0

- [ BREAKING ] Updated to [React 0.14.3](https://facebook.github.io/react/blog/2015/10/07/react-v0.14.html) (thx [Andrey Antukh](https://github.com/niwinz), PR #53)

## 0.5.0

- Added `:class-properties` to define arbitrary properties on a React class (thx [Karanbir Toor](https://github.com/currentoor), PR #44)
- [ BREAKING ] Removed support for `:child-context-types` and `:context-types`. Use `{ :class-properties { :childContextTypes ..., :contextTypes ... } }` instead.

## 0.4.2

- Check for `setTimeout` in global scope instead of in window (thx [Alexander Solovyov](https://github.com/piranha), PR #43)

## 0.4.1

- Fixed bug with rum macros emitting wrong namespace. You can now require `rum.core` under any alias you want (thx [Stuart Hinson](https://github.com/stuarth), PR #42)

## 0.4.0

- [ BREAKING ] Core namespace was renamed from `rum` to `rum.core` to supress CLJS warnings

## 0.3.0

- Upgraded to React 0.13.3, Sablono 0.3.6, ClojueScript 1.7.48
- New API to access context: `child-context`, `child-context-types`, `context-types` (thx [Karanbir Toor](https://github.com/currentoor), PR #37)
- New `defcc` macro for when you only need React component, not the whole Rum state
- [ BREAKING ] Component inner state (`:rum/state`) was moved from `props` to `state`. It doesn’t change a thing if you were using Rum API only, but might break something if you were relaying on internal details
- Deprecated `rum/with-props` macro, use `rum/with-key` or `rum/with-ref` fns instead

## 0.2.7

- Allow components to refer to themselves (thx [Kevin Lynagh](https://github.com/lynaghk), pull request #30)
- Support for multi-arity render fns (issue #23)

## 0.2.6

- Added `local` mixin

## 0.2.5

- Fixed argument destructuring in defc macro (issue #22)

## 0.2.4

- `will-update` and `did-update` lifecycle methods added (thx [Andrey Vasenin](https://github.com/avasenin), pull request #18)

## 0.2.3

- Components defined via `defc/defcs` will have `displayName` defined (thx [Ivan Dubrov](https://github.com/idubrov), pull request #16)
- Not referencing `requestAnimationFrame` when used in headless environment (thx @[whodidthis](https://github.com/whodidthis), pull request #14)

## 0.2.2

- Compatibility with clojurescript 0.0-2758, macros included automatically when `(:require rum)`

## 0.2.1

- Updated deps to clojurescript 0.0-2727, react 0.12.2-5 and sablono 0.3.1

## 0.2.0

- [ BREAKING ] New syntax for mixins: `(defc name < mixin1 mixin2 [args] body...)`
- New `defcs` macro that adds additional first argument to render function: `state`
- Ability to specify `key` and `ref` to rum components via `with-props`

## 0.1.1

- Fixed a bug when render-loop tried to `.forceUpdate` unmounted elements
- Fixed a cursor leak bug in `reactive` mixin
- Removed `:should-update` from `reactive`, it now will be re-rendered if re-created by top-level element
- Combine `reactive` with `static` to avoid re-rendering if component is being recreated with the same args

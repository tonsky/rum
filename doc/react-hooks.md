# React Hooks

Starting from `0.11.5` Rum provides wrappers for a set of React Hooks that can be used from Rum components.

## Limitations

First things first, hooks can be used only inside of `defc` components with optional `rum/static` mixin that enables component's memoization based on its arguments. The reason for that is that React Hooks work only in function-based components and Rum generates those for `defc` components that are not using mixins, because mixins are meant to be executed in lifecycle methods of class-based components. With that make sure that you are not using both hooks and mixins in a single component, otherwise Rum fallsback to generating class-based components and React will throw an expection about incorrect usage of hooks.

## About rum/static

`rum/static` is a mixin that enables component's memoization based on arguments. In class-based components it declares `shoudComponentUpdate` method that compares previous and new arguments. When used as the only mixin in `defc` we generate function-based component and use `rum/static` as a compile-time marker to generate `React.memo` wrapper for a component. `React.memo` is the same as `shoudComponentUpdate`, but meant to be used with function-based components.

## Local state hook

`rum/use-state` takes initial state value and returns a tuple, where the first entry is current state and the second one is a function that takes a new state and schedules an update of the component. It's important to understand that the update is scheduled, which means that the component will be re-rendered eventually, not syncronously. Additionally instead of initla value the hook can take a function that computes initial state, so that the initial value won't be recomputed on every update, but only once, when component is instantiated. 

```clojure
(rum/defc input-field []
  (let [[value set-value!] (rum/use-state "")]
    [:input {:value value
             :on-change #(set-value! (.. % -target -value))}]))
```

## Effect hook

`rum/use-effect!` can be thought of as a replacement of `:did-mount`, `:will-unmount` and `:did-update` mixins. The hook takes a setup function which optionally can return a cleanup function. The former is meant to be used for side-effects execution and the latter to cleanup the result of that operation, if needed.

In this example the component will setup a global `keydown` handler after every update and remove the handler right before every update. This makes sense in cases when the handler can schedule another update for example by updating local state, so we don't want this to happen when the component is already in update phase.

```clojure
(rum/defc input-field []
  (rum/use-effect!
    (fn []
      (let [handler #(println :key (.-key %))]
        (.addEventListener js/document "keydown" handler)
        #(.removeEventListener js/document "keydown" handler))))
  ...)
```

But when you only want to setup event listener once, when component is instantiated and remove it right before component gets removed from UI tree you should use the second argument to the hook, which is a collection of dependencies. Dependencies should be used for conditional execution of hooks. If previous deps are different from new ones, after an update, then the hook will re-execute. In case when we want it to execute only once, on mount and before unmount, deps collection should be an empty collection, just `[]`.

## Limitation of dependencies collection

While the collection itself can be either JS Array or Clojure's Vector etc. the entries will be always compared by identity `identical?`, not by value as you would usually expect this in Clojure. The reason for that is that the eqaulity check is performed on React's side and the API doesn't have a way to provide a custom comparator for us.

## Callback caching hook

`rum/use-callback` caches a callback function based on provided dependencies. In cases when you have a parent component with local state that gives control of updating it to child components via passed callback function child components will be updated every time the parent component updates, even though child components are memoized with `rum/static` mixin. That happens because a callback function is re-created on every update, whicn invalidates memoize child components. The hook is able to cache the callback so that it's not re-created unless the dependencies change.

```clojure
(rum/defc list-item* < rum/static [on-click]
  ...)

(rum/defc list-item [idx on-click]
  (let [handle-click (rum/use-callback #(on-click idx %) [idx])
    [list-item* handle-click]]))
```

## Memoization hook

`rum/use-memo` is meant to be used for caching values and expensive computations, again re-evalution is controlled by deps collection. In the example below the component caches an instance of a class from some JavaScript library so that it's not re-instantiated on every update even though `config` doesn't change.

```clojure
(rum/defc component [config]
  (let [js-lib (rum/use-memo #(js/LibClass. config) [config])]
    ...))
```

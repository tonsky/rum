# Optimizations

## When to use `rum/static` mixin?

`rum/static` applies `shouldComponentUpdate` or `React.memo`, in case of hooks-based components, optimization to underlying React component. The role of this optimization is to check if component's arguments are different from ones passed-in in the previous render of the component. If they didn't change, then React will skip running the component and will reuse returned value from previous render. In other words it's a memoization of React components based on the arguments from the most recent call to a component. 

It's tempting to apply this optimization to every single component when using React in ClojureScript, because we know that equality check on immutable data is free.

Unfortunately this is not entirely true. Understanding this is important when making a decision to optimize particular component. 

Equality check on immutable data is fast when it falls into identity check, for example `=` operation in this case will short-circuit on `identical?` check (which is performed inside of `=`).
```clojure
(def x {:key :value})

(= x x)
```

On the other hand comparing two values created from scratch will perform value equality operation (deep equals), which walks both entire data structures to figure out if they are equal. 
```clojure
(= {:key :value} {:key :value})
```

### When `=` is fast?

Structural sharing is what makes equality check fast on immutable data structures. This obviously doesn't apply to values created from scratch, as in the example above, because they don't have shared structure.

But updating a value and comparing the result to the original value will be faster.
```clojure
(def x {:x {:a 1 :b 2}
        :y {:c 3 :d 4}})

(def y (update-in x [:x :a] inc))

(= x y)
```
In the above case `x` and `y` share `:y {:c 3 :d 4}` part, which will be checked only with `identitcal?` and then `:x {:a 1 :b 2}` will be traversed fully, since this path is updated in `y` and thus not `identitcal?` anymore.

In the context of React, patterns with central data store, such as re-frame, benefit from structural sharing, because re-frame is updating subtrees of the original value. Taking those subtrees from central data store and passing them into memoized React components will result in an efficient memoization, where most of the time equality check will be short-circuited with `identical?` call.

But creating data locally in a component, such as a hash map of attributes, means that on every run of a component those values has to be compared by value (deep equals), because they are created from scratch (no shared structure).

How to use this information now? The thing is that sometimes running a component again would be cheaper than comparing its arguments, especially for components that are frequently updated with a different set of values. That also depends on React wrapper library that you are using. For example in Reagent, where Hiccup is interpreted at runtime it's very likely might be the case that memoizing a component would be cheaper than running it, since Hiccup has to be transformed into `React.createElement` calls. In Rum for example, where most of the Hiccup is pre-compiled into React calls via `defc` macro the perf hit is lower than in Reagent, thus the possibility of usefulness of memoization is lower.

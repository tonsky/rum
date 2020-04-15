# React Suspense and code-splitting

> This requires Rum 0.11.5-SNAPSHOT or newer

[React Suspense](https://reactjs.org/docs/code-splitting.html) allows loading components lazily from code chunks while displaying a fallback UI when a chunk is loading.

Create build configuration in `build.edn`. `:modules` describe code chunks with their corresponding entry points, read more about code-splitting at [https://clojurescript.org/guides/code-splitting](https://clojurescript.org/guides/code-splitting)

```clojure
{:output-dir "resources/public/out"
 :asset-path "out"
 :optimizations :advanced
 :modules    {:core
              {:entries   #{example.core}
               :output-to "resources/public/out/core.js"}
              :components
              {:entries   #{example.components}
               :output-to "resources/public/out/components.js"}}}
```

In `example.components` namespace we declare a component that will be used later in `example.core` ns, which will be in another chunk. For that to work we have to instruct explicitly chunks loader runtime that it was loaded. `cljs.loader/set-loaded!` takes the name of the chunk as specified in build config.

```clojure
(ns example.components
  (:require [rum.core :as rum]
            [cljs.loader :as loader]))

(rum/defc alert [arg]
  [:h1 arg])

(loader/set-loaded! :components)
```

In `example.core` we do the same to instruct about when chunk loading is done, but we also using `require-lazy` macro to require `alert` component from a namespace in another chunk. Then the component is wrapped in `suspense` component that takes care of loading and displaying a fallback.

```clojure
(ns example.core
  (:require [cljs.loader :as loader]
            [rum.core :as rum]
            [rum.lazy-loader :refer [require-lazy]]))

(require-lazy '[example.components :refer [alert]])

(rum/defc root []
  (rum/suspense {:fallback "Hello!"}
    (alert "ARGUMENT")))

(loader/set-loaded! :core)

(rum/mount (root) (.getElementById js/document "root"))
```

Now if you build the code `clj -m cljs.main -co build.edn -c example.core` you'll get 3 chunks: `core.js`, `components.js` and `cljs_base.js` which includes the code shared between those two chunks.

Your HTML should look like this.

```html
<div id="root"></div>
<script src="/out/cljs_base.js"></script>
<script src="/out/core.js"></script>
```

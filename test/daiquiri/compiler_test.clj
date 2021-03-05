(ns daiquiri.compiler-test
  (:require [clojure.test :refer :all]
            [clojure.walk :refer [prewalk]]
            [daiquiri.compiler :as compiler]
            [daiquiri.core :refer [html]]
            [cljs.analyzer :as ana]
            [cljs.env :as env]
            [cljs.compiler :as comp]
            [cljs.core]))

;; Ported from https://github.com/r0man/sablono/blob/master/test/sablono/compiler_test.clj

(defmacro with-compiler-env [[env-sym] & body]
  `(binding [ana/*cljs-static-fns* true]
     (env/with-compiler-env (env/default-compiler-env)
       (let [~env-sym (assoc-in (ana/empty-env) [:ns :name] 'cljs.user)]
         ~@body))))

(defn analyze
  ([env form]
   (env/ensure (ana/analyze env form)))
  ([env form name]
   (env/ensure (ana/analyze env form name)))
  ([env form name opts]
   (env/ensure (ana/analyze env form name opts))))

(defn emit [ast]
  (env/ensure (comp/emit ast)))

(defn replace-gensyms [forms]
  (prewalk
   (fn [form]
     (if (and (symbol? form)
              (re-matches #"attrs\d+" (str form)))
       'attrs form))
   forms))

(defn ===
  "Same as clojure.core/=, but strips of numbers from gensyms before comparison."
  [& more]
  (->> (map replace-gensyms more)
       (apply =)))

(deftest test-compile-attrs
  (are [attrs expected] (=== expected (compiler/compile-attrs attrs))

    nil nil

    {:class "my-class"}
    '(js* "{'className':~{}}" "my-class")

    {:class '(identity "my-class")}
    '(js* "{'className':~{}}" (daiquiri.util/join-classes (identity "my-class")))

    {:class "my-class" :style {:background-color "black"}}
    '(js* "{'style':~{},'className':~{}}" (js* "{'backgroundColor':~{}}" "black") "my-class")

    {:class '(identity "my-class") :style {:background-color '(identity "black")}}
    '(js* "{'style':~{},'className':~{}}"
          (js* "{'backgroundColor':~{}}" (identity "black"))
          (daiquiri.util/join-classes (identity "my-class")))

    {:id :XY}
    '(js* "{'id':~{}}" "XY")))

(comment
  (compiler/to-js {:key ["val"]}))

(deftest test-to-js
  (are [v expected] (= expected (compiler/to-js v))
    nil nil
    :key "key"
    [] '(cljs.core/array)
    {} '(js* "{}")
    [nil 1 :key {}] '(cljs.core/array nil 1 "key" (js* "{}"))
    {:key ["val"]} '(js* "{'key':~{}}" (cljs.core/array "val"))))

(defmacro are-html [& body]
  `(are [form# expected#]
        (=== (macroexpand-1 `(html ~form#)) expected#)
     ~@body))

(deftest test-compile-html
  (testing "basic tags"
    (are-html
     '[:div] '(daiquiri.core/create-element "div" nil nil)
     '["div"] '(daiquiri.core/create-element "div" nil nil)
     '['div] '(daiquiri.core/create-element "div" nil nil)))
  (testing "tag syntax sugar"
    (are-html
     '[:#foo] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" "foo") nil)
     '[:div#foo] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" "foo") nil)
     '[:div.foo] '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "foo") nil)
     '[:.foo] '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "foo") nil)
     '[:div.a.b] '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a b") nil)
     '[:div#foo.bar] '(daiquiri.core/create-element "div" (js* "{'id':~{},'className':~{}}" "foo" "bar") nil)
     '[:div.foo (str "bar" "baz")] '(clojure.core/let [attrs (str "bar" "baz")]
                                      (daiquiri.core/create-element "div"
                                                                    (if (clojure.core/map? attrs)
                                                                      (daiquiri.interpreter/element-attributes (daiquiri.normalize/merge-with-class {:class ["foo"]} attrs))
                                                                      (js* "{'className':~{}}" "foo"))
                                                                    (if (clojure.core/map? attrs) nil (cljs.core/array (daiquiri.compiler/interpret-maybe attrs)))))))
  (testing "tags containing text"
    (are-html
     '[:text "Lorem Ipsum"] '(daiquiri.core/create-element "text" nil (cljs.core/array "Lorem Ipsum"))))
  (testing "contents are concatenated"
    (are-html
     '[:div "foo" "bar"] '(daiquiri.core/create-element "div" nil (cljs.core/array "foo" "bar"))
     '[:div [:p] [:br]] '(daiquiri.core/create-element "div" nil
                                                       (cljs.core/array
                                                        (daiquiri.core/create-element "p" nil nil)
                                                        (daiquiri.core/create-element "br" nil nil)))))
  (testing "seqs are expanded"
    (are-html
     '[:div (list "foo" "bar")] '(clojure.core/let [attrs (list "foo" "bar")]
                                   (daiquiri.core/create-element "div"
                                                                 (if (clojure.core/map? attrs) (daiquiri.interpreter/element-attributes attrs) nil)
                                                                 (if (clojure.core/map? attrs) nil (cljs.core/array (daiquiri.compiler/interpret-maybe attrs)))))
     '(list [:p "a"] [:p "b"])
     '(daiquiri.compiler/interpret-maybe (list [:p "a"] [:p "b"]))))
  (testing "tags can contain tags"
    (are-html
     '[:div [:p]] '(daiquiri.core/create-element "div" nil (cljs.core/array (daiquiri.core/create-element "p" nil nil)))
     '[:p [:span [:a "foo"]]] '(daiquiri.core/create-element "p" nil
                                                             (cljs.core/array
                                                              (daiquiri.core/create-element "span" nil
                                                                                            (cljs.core/array (daiquiri.core/create-element "a" nil (cljs.core/array "foo")))))))))

(deftest test-attributes
  (testing "tag with empty attribute map"
    (are-html
     '[:div {}] '(daiquiri.core/create-element "div" nil nil)))
  (testing "tag with populated attribute map"
    (are-html
     '[:div {:min "1", :max "2"}] '(daiquiri.core/create-element "div" (js* "{'min':~{},'max':~{}}" "1" "2") nil)
     '[:img {"id" "foo"}] '(daiquiri.core/create-element "img" (js* "{'id':~{}}" "foo") nil)
     '[:img {:id "foo"}] '(daiquiri.core/create-element "img" (js* "{'id':~{}}" "foo") nil)))
  (testing "attribute values are escaped"
    (are-html
     '[:div {:id "\""}] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" "\"") nil)))
  (testing "attributes are converted to their ReactDOM equivalents"
    (are-html
     '[:div {:class "classy"}] '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "classy") nil)
     '[:div {:data-foo-bar "baz"}] '(daiquiri.core/create-element "div" (js* "{'data-foo-bar':~{}}" "baz") nil)
     '[:label {:for "foo"}] '(daiquiri.core/create-element "label" (js* "{'htmlFor':~{}}" "foo") nil)))
  (testing "boolean attributes"
    (are-html
     '[:input {:type "checkbox" :checked true}] '(daiquiri.core/create-element "input" (js* "{'type':~{},'checked':~{}}" "checkbox" true) nil)
     '[:input {:type "checkbox" :checked false}] '(daiquiri.core/create-element "input" (js* "{'type':~{},'checked':~{}}" "checkbox" false) nil)))
  (testing "nil attributes"
    (are-html
     '[:span {:class nil} "foo"] '(daiquiri.core/create-element "span" (js* "{'className':~{}}" nil) (cljs.core/array "foo"))))
  (testing "empty attributes"
    (are-html
     '[:span {} "foo"] '(daiquiri.core/create-element "span" nil (cljs.core/array "foo"))))
  (testing "tag with aria attributes"
    (are-html
     '[:div {:aria-disabled true}] '(daiquiri.core/create-element "div" (js* "{'aria-disabled':~{}}" true) nil)))
  (testing "tag with data attributes"
    (are-html
     '[:div {:data-toggle "modal" :data-target "#modal"}] '(daiquiri.core/create-element "div" (js* "{'data-toggle':~{},'data-target':~{}}" "modal" "#modal") nil)))
  (testing "tag with custom attributes"
    (are-html
     '[:div {"ng-hello" "hello"}] '(daiquiri.core/create-element "div" (js* "{'ng-hello':~{}}" "hello") nil))))

(deftest compiled-tags
  (testing "tag content can be vars, and vars can be type-hinted with some metadata"
    (let [x "foo"
          y {:id "id"}]
      (are-html
       '[:span x] '(clojure.core/let [attrs x]
                     (daiquiri.core/create-element "span"
                                                   (if (clojure.core/map? attrs) (daiquiri.interpreter/element-attributes attrs) nil)
                                                   (if (clojure.core/map? attrs) nil (cljs.core/array (daiquiri.compiler/interpret-maybe attrs)))))
       '[:span ^:attrs y] '(clojure.core/let [attrs y]
                             (daiquiri.core/create-element "span" (daiquiri.interpreter/element-attributes attrs) nil)))))
  (testing "tag content can be forms, and forms can be type-hinted with some metadata"
    (are-html
     '[:span (str (+ 1 1))] '(clojure.core/let [attrs (str (+ 1 1))]
                               (daiquiri.core/create-element "span"
                                                             (if (clojure.core/map? attrs) (daiquiri.interpreter/element-attributes attrs) nil)
                                                             (if (clojure.core/map? attrs) nil (cljs.core/array (daiquiri.compiler/interpret-maybe attrs)))))
     [:span ({:foo "bar"} :foo)] '(daiquiri.core/create-element "span" nil (cljs.core/array "bar"))
     '[:span ^:attrs (merge {:type "button"} attrs)] '(clojure.core/let [attrs (merge {:type "button"} attrs)]
                                                        (daiquiri.core/create-element "span" (daiquiri.interpreter/element-attributes attrs) nil))))
  (testing "attributes can contain vars"
    (let [id "id"]
      (are-html
       '[:div {:id id}] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" id) (cljs.core/array))
       '[:div {:id id} "bar"] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" id) (cljs.core/array "bar")))))
  (testing "attributes are evaluated"
    (are-html
     '[:img {:src (str "/foo" "/bar")}] '(daiquiri.core/create-element "img" (js* "{'src':~{}}" (str "/foo" "/bar")) (cljs.core/array))
     '[:div {:id (str "a" "b")} (str "foo")] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" (str "a" "b"))
                                                                            (cljs.core/array (daiquiri.compiler/interpret-maybe (str "foo"))))))
  (testing "type hints"
    ;; TODO. Use cljs type inference
    (let [string "x"]
      (are-html
       '[:span ^String string] '(daiquiri.core/create-element "span" nil (cljs.core/array string)))))
  (testing "values are evaluated only once"
    (let [times-called (atom 0)
          foo #(swap! times-called inc)]
      (macroexpand `(html ~[:div (foo)]))
      (is (= @times-called 1)))))

(deftest fragments
  (testing "React 16 fragment syntactic support"
    (are-html
     '[:*] '(daiquiri.core/create-element daiquiri.core/fragment nil nil)
     '[:<>] '(daiquiri.core/create-element daiquiri.core/fragment nil nil)

     '[:* [:p]] '(daiquiri.core/create-element daiquiri.core/fragment nil (cljs.core/array (daiquiri.core/create-element "p" nil nil)))
     '[:<> [:p]] '(daiquiri.core/create-element daiquiri.core/fragment nil (cljs.core/array (daiquiri.core/create-element "p" nil nil)))

     '[:* [:p] [:p]] '(daiquiri.core/create-element daiquiri.core/fragment nil
                                                    (cljs.core/array (daiquiri.core/create-element "p" nil nil) (daiquiri.core/create-element "p" nil nil)))
     '[:<> [:p] [:p]] '(daiquiri.core/create-element daiquiri.core/fragment nil
                                                     (cljs.core/array (daiquiri.core/create-element "p" nil nil) (daiquiri.core/create-element "p" nil nil)))

     '[:dl (for [n (range 2)]
             [:* {:key n}
              [:dt {} (str "term " n)]
              [:dd {} (str "definition " n)]])]
     '(daiquiri.core/create-element
       "dl"
       nil
       (cljs.core/array
        (into-array
         (clojure.core/for
          [n (range 2)]
           (daiquiri.core/create-element
            daiquiri.core/fragment
            (js* "{'key':~{}}" n)
            (cljs.core/array
             (daiquiri.core/create-element "dt" nil (cljs.core/array (daiquiri.compiler/interpret-maybe (str "term " n))))
             (daiquiri.core/create-element "dd" nil (cljs.core/array (daiquiri.compiler/interpret-maybe (str "definition " n)))))))))))))

(deftest test-issue-2-merge-class
  (are-html
   '[:div.a {:class (if (true? true) "true" "false")}] '(daiquiri.core/create-element "div"
                                                                                      (js* "{'className':~{}}" (daiquiri.util/join-classes ["a" (if (true? true) "true" "false")]))
                                                                                      (cljs.core/array))
   '[:div.a.b {:class (if (true? true) ["true"] "false")}] '(daiquiri.core/create-element "div"
                                                                                          (js* "{'className':~{}}" (daiquiri.util/join-classes ["a" "b" (if (true? true) ["true"] "false")]))
                                                                                          (cljs.core/array))))

(deftest test-issue-3-recursive-js-literal
  (are-html
   '[:div.interaction-row {:style {:position "relative"}}] '(daiquiri.core/create-element "div"
                                                                                          (js* "{'style':~{},'className':~{}}" (js* "{'position':~{}}" "relative") "interaction-row")
                                                                                          nil))
  (let [username "foo"
        hidden #(if %1 {:display "none"} {:display "block"})]
    (are-html
     '[:ul.nav.navbar-nav.navbar-right.pull-right
       [:li.dropdown {:style (hidden (nil? username))}
        [:a.dropdown-toggle {:role "button" :href "#"} (str "Welcome, " username)
         [:span.caret]]
        [:ul.dropdown-menu {:role "menu" :style {:left 0}}]]]
     '(daiquiri.core/create-element
       "ul"
       (js* "{'className':~{}}" "nav navbar-nav navbar-right pull-right")
       (cljs.core/array
        (daiquiri.core/create-element
         "li"
         (js* "{'style':~{},'className':~{}}" (daiquiri.interpreter/element-attributes (hidden (nil? username))) "dropdown")
         (cljs.core/array
          (daiquiri.core/create-element
           "a"
           (js* "{'role':~{},'href':~{},'className':~{}}" "button" "#" "dropdown-toggle")
           (cljs.core/array
            (daiquiri.compiler/interpret-maybe (str "Welcome, " username))
            (daiquiri.core/create-element "span" (js* "{'className':~{}}" "caret") nil)))
          (daiquiri.core/create-element
           "ul"
           (js* "{'role':~{},'style':~{},'className':~{}}" "menu" (js* "{'left':~{}}" 0) "dropdown-menu")
           nil))))))))

(deftest test-issue-22-id-after-class
  (are-html
   [:div.well#setup] '(daiquiri.core/create-element "div" (js* "{'id':~{},'className':~{}}" "setup" "well") nil)))

(deftest test-issue-25-comma-separated-class
  (are-html
   '[:div.c1.c2 "text"] '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "c1 c2") (cljs.core/array "text"))
   '[:div.aa (merge {:class "bb"})] '(clojure.core/let [attrs (merge {:class "bb"})]
                                       (daiquiri.core/create-element "div"
                                                                     (if (clojure.core/map? attrs)
                                                                       (daiquiri.interpreter/element-attributes (daiquiri.normalize/merge-with-class {:class ["aa"]} attrs))
                                                                       (js* "{'className':~{}}" "aa"))
                                                                     (if (clojure.core/map? attrs) nil (cljs.core/array (daiquiri.compiler/interpret-maybe attrs)))))))

(deftest test-issue-33-number-warning
  (are-html
   '[:div (count [1 2 3])] '(clojure.core/let [attrs (count [1 2 3])]
                              (daiquiri.core/create-element "div"
                                                            (if (clojure.core/map? attrs) (daiquiri.interpreter/element-attributes attrs) nil)
                                                            (if (clojure.core/map? attrs) nil (cljs.core/array (daiquiri.compiler/interpret-maybe attrs)))))))

(deftest test-issue-37-camel-case-style-attrs
  (are-html
   '[:div {:style {:z-index 1000}}] '(daiquiri.core/create-element "div" (js* "{'style':~{}}" (js* "{'zIndex':~{}}" 1000)) nil)))

(deftest shorthand-div-forms
  (are-html
   [:#test] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" "test") nil)
   '[:.klass] '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "klass") nil)
   '[:#test.klass] '(daiquiri.core/create-element "div" (js* "{'id':~{},'className':~{}}" "test" "klass") nil)
   '[:#test.klass1.klass2] '(daiquiri.core/create-element "div" (js* "{'id':~{},'className':~{}}" "test" "klass1 klass2") nil)
   '[:.klass1.klass2#test] '(daiquiri.core/create-element "div" (js* "{'id':~{},'className':~{}}" "test" "klass1 klass2") nil)))

(deftest test-namespaced-fn-call
  (are-html
   '(some-ns/comp "arg") '(daiquiri.compiler/interpret-maybe (some-ns/comp "arg"))
   '(some.ns/comp "arg") '(daiquiri.compiler/interpret-maybe (some.ns/comp "arg"))))

(defmacro expand-html [form]
  `(macroexpand-1 '(html ~form)))

(deftest test-compile-div-with-nested-lazy-seq
  (is (=== (expand-html [:div (map identity ["A" "B"])])
           '(clojure.core/let [attrs (map identity ["A" "B"])]
              (daiquiri.core/create-element "div"
                                            (if (clojure.core/map? attrs) (daiquiri.interpreter/element-attributes attrs) nil)
                                            (if (clojure.core/map? attrs) nil (cljs.core/array (daiquiri.compiler/interpret-maybe attrs))))))))

(deftest test-compile-div-with-nested-list
  (is (= (expand-html [:div '("A" "B")])
         '(daiquiri.core/create-element "div" nil (cljs.core/array "A" "B")))))

(deftest test-compile-div-with-nested-vector
  (is (= (expand-html [:div ["A" "B"]])
         '(daiquiri.core/create-element "div" nil (cljs.core/array "A" "B"))))
  (is (=== (expand-html [:div (vector "A" "B")])
           '(clojure.core/let [attrs (vector "A" "B")]
              (daiquiri.core/create-element "div"
                                            (if (clojure.core/map? attrs) (daiquiri.interpreter/element-attributes attrs) nil)
                                            (if (clojure.core/map? attrs) nil (cljs.core/array (daiquiri.compiler/interpret-maybe attrs))))))))

(deftest test-class-as-set
  (is (= (expand-html [:div.a {:class #{"a" "b" "c"}}])
         '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a a b c") nil))))

(deftest test-class-as-list
  (is (= (expand-html [:div.a {:class (list "a" "b" "c")}])
         '(daiquiri.core/create-element "div"
                                        (js* "{'className':~{}}" (daiquiri.util/join-classes ["a" (list "a" "b" "c")]))
                                        (cljs.core/array)))))

(deftest test-class-as-vector
  (is (= (expand-html [:div.a {:class (vector "a" "b" "c")}])
         '(daiquiri.core/create-element "div"
                                        (js* "{'className':~{}}" (daiquiri.util/join-classes ["a" (vector "a" "b" "c")]))
                                        (cljs.core/array)))))

(deftest test-class-merge-symbol
  (let [class #{"b"}]
    (are-html
     [:div.a {:class class}]
     '(daiquiri.core/create-element "div"
                                    (js* "{'className':~{}}" "a b")
                                    nil))))

(deftest test-issue-90
  (is (= (expand-html [:div nil (case :a :a "a")])
         '(daiquiri.core/create-element "div" nil (cljs.core/array nil (clojure.core/case :a :a "a"))))))

(deftest test-compile-attr-class
  (are [form expected]
       (= expected (compiler/compile-attr :class form))
    nil nil
    "foo" "foo"
    '("foo" "bar") "foo bar"
    ["foo" "bar"] "foo bar"
    #{"foo" "bar"} "foo bar"
    '(set "foo" "bar") '(daiquiri.util/join-classes (set "foo" "bar"))
    '[(list "foo" "bar")] '(daiquiri.util/join-classes [(list "foo" "bar")])))

(deftest test-optimize-let-form
  (is (= (expand-html (let [x "x"] [:div "x"]))
         '(clojure.core/let [x "x"]
            (daiquiri.core/create-element "div" nil (cljs.core/array "x"))))))

(deftest test-optimize-for-loop
  (is (=== (expand-html [:ul (for [n (range 3)] [:li n])])
           '(daiquiri.core/create-element "ul" nil
                                          (cljs.core/array
                                           (into-array
                                            (clojure.core/for
                                             [n (range 3)]
                                              (clojure.core/let
                                               [attrs n]
                                                (daiquiri.core/create-element
                                                 "li"
                                                 (if (clojure.core/map? attrs) (daiquiri.interpreter/element-attributes attrs) nil)
                                                 (if (clojure.core/map? attrs) nil (cljs.core/array (daiquiri.compiler/interpret-maybe attrs)))))))))))
  (is (=== (expand-html [:ul (for [n (range 3)] [:li ^:attrs n])])
           '(daiquiri.core/create-element "ul" nil
                                          (cljs.core/array
                                           (into-array
                                            (clojure.core/for
                                             [n (range 3)]
                                              (clojure.core/let [attrs n]
                                                (daiquiri.core/create-element "li" (daiquiri.interpreter/element-attributes attrs) nil)))))))))

(deftest test-compile-case
  (is (= (expand-html [:div {:class "a"}
                       (case "a"
                         "a" [:div "a"]
                         "b" [:div "b"]
                         [:div "else"])])
         '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a")
                                        (cljs.core/array
                                         (clojure.core/case "a"
                                           "a" (daiquiri.core/create-element "div" nil (cljs.core/array "a"))
                                           "b" (daiquiri.core/create-element "div" nil (cljs.core/array "b"))
                                           (daiquiri.core/create-element "div" nil (cljs.core/array "else"))))))))

(deftest test-compile-cond
  (is (= (expand-html [:div {:class "a"}
                       (cond
                         "a" [:div "a"]
                         "b" [:div "b"]
                         :else [:div "else"])])
         '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a")
                                        (cljs.core/array
                                         (clojure.core/cond
                                           "a" (daiquiri.core/create-element "div" nil (cljs.core/array "a"))
                                           "b" (daiquiri.core/create-element "div" nil (cljs.core/array "b"))
                                           :else (daiquiri.core/create-element "div" nil (cljs.core/array "else"))))))))

(deftest test-compile-condp
  (is (= (expand-html [:div {:class "a"}
                       (condp = "a"
                         "a" [:div "a"]
                         "b" [:div "b"]
                         [:div "else"])])
         '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a")
                                        (cljs.core/array
                                         (clojure.core/condp = "a"
                                           "a" (daiquiri.core/create-element "div" nil (cljs.core/array "a"))
                                           "b" (daiquiri.core/create-element "div" nil (cljs.core/array "b"))
                                           (daiquiri.core/create-element "div" nil (cljs.core/array "else"))))))))

(deftest test-optimize-if
  (is (= (expand-html (if true [:span "foo"] [:span "bar"]))
         '(if true
            (daiquiri.core/create-element "span" nil (cljs.core/array "foo"))
            (daiquiri.core/create-element "span" nil (cljs.core/array "bar"))))))

(deftest test-compile-if-not
  (is (= (expand-html [:div {:class "a"} (if-not false [:div [:div]])])
         '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a")
                                        (cljs.core/array
                                         (clojure.core/if-not false
                                           (daiquiri.core/create-element "div" nil (cljs.core/array (daiquiri.core/create-element "div" nil nil)))))))))

(deftest test-compile-if-some
  (is (= (expand-html [:div {:class "a"} (if-some [x true] [:div [:div]])])
         '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a")
                                        (cljs.core/array
                                         (clojure.core/if-some [x true]
                                           (daiquiri.core/create-element "div" nil (cljs.core/array (daiquiri.core/create-element "div" nil nil)))))))))

(deftest test-issue-115
  (is (= (expand-html [:a {:id :XY}])
         '(daiquiri.core/create-element "a" (js* "{'id':~{}}" "XY") nil))))

(deftest test-issue-130
  (let [css {:table-cell "bg-blue"}]
    (is (= (expand-html [:div {:class (:table-cell css)} [:span "abc"]])
           '(daiquiri.core/create-element "div"
                                          (js* "{'className':~{}}" (daiquiri.util/join-classes [(:table-cell css)]))
                                          (cljs.core/array (daiquiri.core/create-element "span" nil (cljs.core/array "abc"))))))))

(deftest test-issue-141-inline
  (testing "with attributes"
    (is (= (expand-html [:span {} ^:inline (constantly 1)])
           '(daiquiri.core/create-element "span" nil (cljs.core/array (constantly 1))))))
  (testing "without attributes"
    (is (= (expand-html [:span ^:inline (constantly 1)])
           '(daiquiri.core/create-element "span" nil (cljs.core/array (constantly 1)))))))

(deftest test-compile-attributes-non-literal-key
  (is (= (expand-html [:input {(case :checkbox :checkbox :checked :value) "x"}])
         '(daiquiri.core/create-element "input"
                                        (daiquiri.interpreter/element-attributes {(case :checkbox :checkbox :checked :value) "x"})
                                        (cljs.core/array)))))

(deftest test-issue-158
  (is (= (expand-html [:div {:style (merge {:margin-left "2rem"}
                                           (when focused? {:color "red"}))}])
         '(daiquiri.core/create-element "div"
                                        (js* "{'style':~{}}" (daiquiri.interpreter/element-attributes (merge {:margin-left "2rem"} (when focused? {:color "red"}))))
                                        (cljs.core/array)))))

(deftest test-compile-when
  (is (= (expand-html [:div {:class "a"} (when true [:div [:div]])])
         '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a")
                                        (cljs.core/array
                                         (clojure.core/when true
                                           (daiquiri.core/create-element "div" nil (cljs.core/array (daiquiri.core/create-element "div" nil nil)))))))))

(deftest test-compile-when-not
  (is (= (expand-html [:div {:class "a"} (when-not false [:div [:div]])])
         '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a")
                                        (cljs.core/array
                                         (clojure.core/when-not false
                                           (daiquiri.core/create-element "div" nil (cljs.core/array (daiquiri.core/create-element "div" nil nil)))))))))

(deftest test-compile-when-some
  (is (= (expand-html [:div {:class "a"} (when-some [x true] [:div [:div]])])
         '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a")
                                        (cljs.core/array
                                         (clojure.core/when-some [x true]
                                           (daiquiri.core/create-element "div" nil (cljs.core/array (daiquiri.core/create-element "div" nil nil)))))))))

;; type inference
(deftest test-infer-tag-any
  (with-compiler-env [env]
    (is (= '#{any} (compiler/infer-tag env '(my-fn))))))

(deftest test-infer-tag-react-fn
  (with-compiler-env [env]
    (analyze env '(defn ^js/React.Element my-fn []
                    (js/React.createElement "div")))
    (is (= '#{js/React.Element} (compiler/infer-tag env '(my-fn))))))

(deftest test-infer-tag-react-fns
  (with-compiler-env [env]
    (analyze env '(defn ^js/React.Element my-fn-1 []
                    (js/React.createElement "div")))
    (analyze env '(defn my-fn-2 []
                    (my-fn-1)))
    (is (= '#{js/React.Element} (compiler/infer-tag env '(my-fn-2))))))

(deftest test-compile-interpret-maybe
  (with-compiler-env [env]
    (is (= '(daiquiri.interpreter/interpret (my-fn))
           (ana/macroexpand-1 env '(daiquiri.compiler/interpret-maybe (my-fn)))))))

(deftest test-compile-inferred-attribute-map
  (with-compiler-env [env]
    (analyze env '(defn attrs [] {:class "x"}))
    (is (=== (ana/macroexpand-1 env '(daiquiri.core/html [:div (attrs) "content"]))
             '(clojure.core/let [attrs (attrs)]
                (daiquiri.core/create-element "div" (daiquiri.interpreter/element-attributes attrs) (cljs.core/array "content")))))))

(comment
  (with-compiler-env [env]
    (ana/macroexpand-1 env '(daiquiri.compiler/interpret-maybe (cljs.core/array 1 2)))))

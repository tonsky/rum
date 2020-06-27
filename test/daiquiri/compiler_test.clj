(ns daiquiri.compiler-test
  (:require [clojure.test :refer :all]
            [daiquiri.compiler :as compiler]
            [daiquiri.core :refer [html]]))

;; Ported from https://github.com/r0man/sablono/blob/master/test/sablono/compiler_test.clj

(comment
  (compiler/compile-attrs {:id :XY}))

(deftest test-compile-attrs
  (are [attrs expected] (= expected (compiler/compile-attrs attrs))

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
     (= (macroexpand-1 `(html ~form#)) expected#)
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
      #_#_'[:div.foo (str "bar" "baz")] '()))
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
      #_#_'[:div (list "foo" "bar")] '()

      '(list [:p "a"] [:p "b"])
      '(daiquiri.interpreter/interpret (list [:p "a"] [:p "b"]))))
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
      '[:div {:data-toggle "modal" :data-target "#modal"}] '(daiquiri.core/create-element "div" (js* "{'data-toggle':~{},'data-target':~{}}" "modal" "#modal") nil))))

(deftest compiled-tags
  (testing "tag content can be vars, and vars can be type-hinted with some metadata"
    (let [x "foo"
          y {:id "id"}]
      (are-html
        '[:span x] 1
        '[:span ^:attrs y] 2)))
  (testing "tag content can be forms, and forms can be type-hinted with some metadata"
    (are-html
      '[:span (str (+ 1 1))] 1
      [:span ({:foo "bar"} :foo)] '(daiquiri.core/create-element "span" nil (cljs.core/array "bar"))
      '[:span ^:attrs (merge {:type "button"} attrs)] 2))
  (testing "attributes can contain vars"
    (let [id "id"]
      (are-html
        '[:div {:id id}] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" id) (cljs.core/array))
        '[:div {:id id} "bar"] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" id) (cljs.core/array "bar")))))
  (testing "attributes are evaluated"
    (are-html
      '[:img {:src (str "/foo" "/bar")}] '(daiquiri.core/create-element "img" (js* "{'src':~{}}" (str "/foo" "/bar")) (cljs.core/array))
      '[:div {:id (str "a" "b")} (str "foo")] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" (str "a" "b"))
                                                 (cljs.core/array (daiquiri.interpreter/interpret (str "foo"))))))
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
                   (daiquiri.core/create-element "dt" nil (cljs.core/array (daiquiri.interpreter/interpret (str "term " n))))
                   (daiquiri.core/create-element "dd" nil (cljs.core/array (daiquiri.interpreter/interpret (str "definition " n)))))))))))))

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
             (js* "{'style':~{},'className':~{}}" (daiquiri.interpreter/attributes (hidden (nil? username))) "dropdown")
             (cljs.core/array
               (daiquiri.core/create-element
                 "a"
                 (js* "{'role':~{},'href':~{},'className':~{}}" "button" "#" "dropdown-toggle")
                 (cljs.core/array
                   (daiquiri.interpreter/interpret (str "Welcome, " username))
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
    '[:div.aa (merge {:class "bb"})] 1))

(deftest test-issue-33-number-warning
  (are-html
    '[:div (count [1 2 3])] 1))

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
    '(some-ns/comp "arg") '(daiquiri.interpreter/interpret (some-ns/comp "arg"))
    '(some.ns/comp "arg") '(daiquiri.interpreter/interpret (some.ns/comp "arg"))))

(defmacro expand-html [form]
  `(macroexpand-1 '(html ~form)))

(deftest test-compile-div-with-nested-lazy-seq
  (is (= (expand-html [:div (map identity ["A" "B"])])
         1)))

(deftest test-compile-div-with-nested-list
  (is (= (expand-html [:div '("A" "B")])
         '(daiquiri.core/create-element "div" nil (cljs.core/array "A" "B")))))

(deftest test-compile-div-with-nested-vector
  (is (= (expand-html [:div ["A" "B"]])
         '(daiquiri.core/create-element "div" nil (cljs.core/array "A" "B"))))
  (is (= (expand-html [:div (vector "A" "B")])
         12)))

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
    '("foo" "bar" ) "foo bar"
    ["foo" "bar"] "foo bar"
    #{"foo" "bar"} "foo bar"
    '(set "foo" "bar") '(daiquiri.util/join-classes (set "foo" "bar"))
    '[(list "foo" "bar")] '(daiquiri.util/join-classes [(list "foo" "bar")])))

(deftest test-optimize-let-form
  (is (= (expand-html (let [x "x"] [:div "x"]))
         '(clojure.core/let [x "x"]
            (daiquiri.core/create-element "div" nil (cljs.core/array "x"))))))

(deftest test-optimize-for-loop
  (is (= (expand-html [:ul (for [n (range 3)] [:li n])])
         '(daiquiri.core/create-element "ul" nil
            (cljs.core/array
              (into-array
                (clojure.core/for
                  [n (range 3)]
                  (clojure.core/let
                    [attrs3454 n]
                    (daiquiri.core/create-element
                      "li"
                      (if (clojure.core/map? attrs3454) (daiquiri.interpreter/attributes attrs3454) nil)
                      (if (clojure.core/map? attrs3454) nil (cljs.core/array (daiquiri.interpreter/interpret attrs3454)))))))))))
  (is (= (expand-html [:ul (for [n (range 3)] [:li ^:attrs n])])
         '(daiquiri.core/create-element "ul" nil
            (cljs.core/array
              (into-array
                (clojure.core/for
                  [n (range 3)]
                  (clojure.core/let [attrs3464 n] (daiquiri.core/create-element "li" (daiquiri.interpreter/attributes attrs3464) nil)))))))))

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
            (daiquiri.interpreter/attributes {(case :checkbox :checkbox :checked :value) "x"})
            (cljs.core/array)))))

(deftest test-issue-158
  (is (= (expand-html [:div {:style (merge {:margin-left "2rem"}
                                           (when focused? {:color "red"}))}])
         '(daiquiri.core/create-element "div"
            (js* "{'style':~{}}" (daiquiri.interpreter/attributes (merge {:margin-left "2rem"} (when focused? {:color "red"}))))
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



(comment
  (macroexpand-1 '(html [:div.a {:class class}])))

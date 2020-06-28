(ns daiquiri.normalize-test
  (:require [daiquiri.normalize :as normalize]
            #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [are is deftest]])))

;; Ported from https://github.com/r0man/sablono/blob/master/test/sablono/normalize_test.cljc

(deftest test-merge-with-class
  (are [maps expected]
    (= expected (apply normalize/merge-with-class maps))
    []
    nil
    [{:a 1} {:b 2}]
    {:a 1 :b 2}
    [{:a 1 :class :a} {:b 2 :class "b"} {:c 3 :class ["c"]}]
    {:a 1 :b 2 :c 3 :class ["a" "b" "c"]}
    [{:a 1 :class :a} {:b 2 :class "b"} {:c 3 :class (seq ["c"])}]
    {:a 1 :b 2 :c 3 :class ["a" "b" "c"]}
    ['{:a 1 :class ["a"]} '{:b 2 :class [(if true "b")]}]
    '{:a 1 :class ["a" (if true "b")] :b 2}
    ;; Map lookup. Issue #130
    ['{:class (:table-cell csslib)} {}]
    '{:class [(:table-cell csslib)]}))

(deftest test-strip-css
  (are [x expected]
    (= expected (normalize/strip-css x))
    nil nil
    "" ""
    "foo" "foo"
    "#foo" "foo"
    ".foo" "foo"))

(deftest test-match-tag
  (are [tag expected]
    (= expected (normalize/match-tag tag))
    :div ["div" nil []]
    :div#foo ["div" "foo" []]
    :div#foo.bar ["div" "foo" ["bar"]]
    :div.bar#foo ["div" "foo" ["bar"]]
    :div#foo.bar.baz ["div" "foo" ["bar" "baz"]]
    :div.bar.baz#foo ["div" "foo" ["bar" "baz"]]
    :div.bar#foo.baz ["div" "foo" ["bar" "baz"]])
  (let [[tag id classes] (normalize/match-tag :div#foo.bar.baz)]
    (is (= "div" tag))
    (is (= "foo" id))
    (is (= ["bar" "baz"] classes))
    (is (vector? classes))))

(deftest test-normalize-class
  (are [class expected]
    (= expected (normalize/normalize-class class))
    nil nil
    :x ["x"]
    "x" ["x"]
    ["x"] ["x"]
    [:x] ["x"]
    '(if true "x") ['(if true "x")]
    'x ['x]
    '("a" "b") ["a" "b"]))

(deftest test-attributes
  (are [attrs expected]
    (= expected (normalize/attributes attrs))
    nil nil
    {} {}
    {:class nil} {:class nil}
    {:class "x"} {:class ["x"]}
    {:class ["x"]} {:class ["x"]}
    '{:class ["x" (if true "y")]} '{:class ["x" (if true "y")]}))

(deftest test-children
  (are [children expected]
    (= expected (normalize/children children))
    [] []
    1 [1]
    "x" ["x"]
    ["x"] ["x"]
    [["x"]] ["x"]
    [["x" "y"]] ["x" "y"]
    [:div] [[:div]]
    [[:div]] [[:div]]
    [[[:div]]] [[:div]]))

(deftest test-element
  (are [element expected]
    (= expected (normalize/element element))
    [:div] ["div" {} '()]
    [:div {:class nil}] ["div" {:class nil} '()]
    [:div#foo] ["div" {:id "foo"} '()]
    [:div.foo] ["div" {:class ["foo"]} '()]
    [:div.a.b] ["div" {:class ["a" "b"]} '()]
    [:div.a.b {:class "c"}] ["div" {:class ["a" "b" "c"]} '()]
    [:div.a.b {:class nil}] ["div" {:class ["a" "b"]} '()]
    [:div "a" "b"] ["div" {} ["a" "b"]]
    [:div ["a" "b"]] ["div" {} ["a" "b"]]))

(deftest test-element-meta
  (are [element expected]
    (= (->> (nth (normalize/element element) 2)
            (map (comp true? :inline meta)))
       expected)
    '[:span (constantly 1)] [false]
    '[:span ^:inline (constantly 1)] [true]
    '[:span ^:inline (constantly 1) nil ^:inline (constantly 2)] [true true]))

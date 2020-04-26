(ns daiquiri.compiler-test
  (:require [clojure.test :refer :all]
            [daiquiri.compiler :as compiler]))

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
     (= (compiler/compile-html form#) expected#)
     ~@body))

(deftest test-compile-html
  (testing "basic tags"
    (are-html
      [:div] '(daiquiri.core/create-element "div" nil nil)
      ["div"] '(daiquiri.core/create-element "div" nil nil)
      #_#_['div] '(daiquiri.core/create-element "div" nil nil)))
  (testing "tag syntax sugar"
    (are-html
      [:div#foo] '(daiquiri.core/create-element "div" (js* "{'id':~{}}" "foo") nil)
      [:div.foo] '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "foo") nil)
      [:div.a.b] '(daiquiri.core/create-element "div" (js* "{'className':~{}}" "a b") nil)
      [:div#foo.bar] '(daiquiri.core/create-element "div" (js* "{'id':~{},'className':~{}}" "foo" "bar") nil)
      #_#_[:div.foo (str "bar" "baz")] '()))
  (testing "tags containing text"
    (are-html
      [:text "Lorem Ipsum"] '(daiquiri.core/create-element "text" nil (cljs.core/array "Lorem Ipsum"))))
  (testing "contents are concatenated"
    (are-html
      [:div "foo" "bar"] '(daiquiri.core/create-element "div" nil (cljs.core/array "foo" "bar"))
      [:div [:p] [:br]] '(daiquiri.core/create-element "div" nil
                           (cljs.core/array
                             (daiquiri.core/create-element "p" nil nil)
                             (daiquiri.core/create-element "br" nil nil)))))
  #_(testing "seqs are expanded"
      (are-html
        [:div (list "foo" "bar")] '()
        (list [:p "a"] [:p "b"]) '())))

(comment
  (compiler/compile-html [:div (list "foo" "bar")])
  (compiler/compile-html (list [:p "a"] [:p "b"])))

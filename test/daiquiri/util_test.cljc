(ns daiquiri.util-test
  (:require [daiquiri.util :as u]
            #?(:clj [clojure.test :refer :all])
            #?(:cljs [cljs.test :refer-macros [are is testing deftest]])))

;; Ported from https://github.com/r0man/sablono/blob/master/test/sablono/util_test.cljc

(deftest test-camel-case
  (are [attr expected]
    (= expected (u/camel-case attr))
    nil nil
    "" ""
    :data :data
    :data-toggle :data-toggle
    :http-equiv :httpEquiv
    :aria-checked :aria-checked
    '(identity :class) '(identity :class)))

(deftest test-camel-case-keys
  (are [attrs expected]
    (= expected (u/camel-case-keys attrs))
    {:id "x"}
    {:id "x"}
    {:class "x"}
    {:class "x"}
    {:http-equiv "Expires"}
    {:httpEquiv "Expires"}
    {:style {:z-index 1000}}
    {:style {:zIndex 1000}}
    {:on-click '(fn [e] (let [m {:a-b "c"}]))}
    {:onClick '(fn [e] (let [m {:a-b "c"}]))}
    {'(identity :class) "my-class"
     :style {:background-color "black"}}
    {'(identity :class) "my-class"
     :style {:backgroundColor "black"}}))

(deftest test-html-to-dom-attrs
  (are [attrs expected]
    (= expected (u/html-to-dom-attrs attrs))
    {:id "x"}
    {:id "x"}
    {:class "x"}
    {:className "x"}
    {:http-equiv "Expires"}
    {:httpEquiv "Expires"}
    {:style {:z-index 1000}}
    {:style {:zIndex 1000}}
    {:on-click '(fn [e] (let [m {:a-b "c"}]))}
    {:onClick '(fn [e] (let [m {:a-b "c"}]))}
    {'(identity :class) "my-class"
     :style {:background-color "black"}}
    {'(identity :class) "my-class"
     :style {:backgroundColor "black"}}))

(deftest test-element?
  (is (u/element? [:div]))
  (is (not (u/element? nil)))
  (is (not (u/element? [])))
  (is (not (u/element? 1)))
  (is (not (u/element? "x"))))

(deftest test-join-classes
  (are [classes expected]
    (= expected (u/join-classes classes))
    ["a"] "a"
    #{"a"} "a"
    ["a" "b"] "a b"
    #{"a" "b"} "a b"
    ["a" ["b"]] "a b"
    ["a" (set ["a" "b" "c"])] "a a b c"))

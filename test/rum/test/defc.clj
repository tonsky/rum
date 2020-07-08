(ns rum.test.defc
  (:require
   [rum.core]
   [clojure.test :refer [deftest is are testing]]
   [clojure.walk :refer [prewalk]]))

(defn replace-gensyms [sym forms]
  (prewalk
   (fn [form]
     (if (and (symbol? form)
              (re-matches (re-pattern (str sym "\\d+")) (str form)))
       sym
       form))
   forms))

(defmacro eval-in-temp-ns [& forms]
  `(binding [*ns* *ns*]
     (in-ns (gensym))
     (clojure.core/use 'clojure.core)
     (clojure.core/use 'rum.core)
     (eval
      '(do ~@forms))))

;; Copied from Clojure: https://git.io/vwFsG
(deftest defc-error-messages
  (testing "bad name"
    (is (thrown-with-msg?
         IllegalArgumentException
         #"First argument to defc must be a symbol"
         (eval-in-temp-ns (defc "bad docstring" testname [arg1 arg2])))))
  (testing "mixins after argvec"
    (is (thrown-with-msg?
         IllegalArgumentException
         #"Mixins must be given before argument list"
         (eval-in-temp-ns (defc testname "docstring" [arg1 arg2] < misplaced-mixin))))
    (is (thrown-with-msg?
         IllegalArgumentException
         #"Mixins must be given before argument list"
         (eval-in-temp-ns (defc testname "docstring"
                            ([arg1] < misplaced-mixin)
                            ([arg1 arg2] < misplaced-mixin)))))
    (is (thrown-with-msg?
         IllegalArgumentException
         #"Mixins must be given before argument list"
         (eval-in-temp-ns (defc testname
                            ([arg1] < misplaced-mixin)
                            ([arg1 arg2] < misplaced-mixin)))))))

(deftest defc-conditions
  (testing "no conditions supplied"
    (is (= (replace-gensyms 'pre-post-test
                            (#'rum.core/-defc 'rum.core/build-defc
                                              {:ns {:name 'core}} ; cljs?
                                              '(pre-post-test ([y] {:x 1})
                                                              ([y z] (+ y z 1)))))
           '(def pre-post-test
              (rum.core/lazy-build rum.core/build-defc
                                   (clojure.core/fn
                                     ([y] (do {:x 1}))
                                     ([y z] (do (daiquiri.compiler/interpret-maybe (+ y z 1)))))
                                   nil
                                   "core/pre-post-test")))))

  (testing "some conditions supplied"
    (is (= (replace-gensyms 'pre-post-test
                            (#'rum.core/-defc 'rum.core/build-defc
                                              {:ns {:name 'core}} ; cljs?
                                              '(pre-post-test ([y] {:pre [(pos? y)]} {:x 1})
                                                              ([y z] (+ y z 1)))))
           '(def pre-post-test
              (rum.core/lazy-build rum.core/build-defc
                                   (clojure.core/fn
                                     ([y] {:pre [(pos? y)]} (do {:x 1}))
                                     ([y z] (do (daiquiri.compiler/interpret-maybe (+ y z 1)))))
                                   nil
                                   "core/pre-post-test"))))))

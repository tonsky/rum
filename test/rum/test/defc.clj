(ns rum.test.defc
  (:require
    [rum.core]
    [clojure.string :as str]
    [clojure.test :refer [deftest is are testing]]
    [clojure.java.shell :as shell]))

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
          (eval-in-temp-ns (defc "bad docstring" testname [arg1 arg2]))))))

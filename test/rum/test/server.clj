(ns rum.test.server
  (:require
    [rum.core :as rum]
    [clojure.test :refer [deftest is are testing]]))


(rum/defcs comp-mixins < (rum/local 7)
                         { :will-mount (fn [s] (assoc  s ::key 1 ::x 2))
                           :did-mount  (fn [s] (-> s
                                                   (update ::key inc)
                                                   (dissoc ::x))) }
  [state]
  [:div
    [:.local @(:rum/local state)]
    [:.key   (::key state)]
    [:.x     (::x state)]])


(deftest test-lifecycle
  (is (= (comp-mixins)
         [:div
           [:.local 7]
           [:.key   2]
           [:.x     nil]])))

(rum/defc comp-arglists
  ([a])
  ([a b])
  ([a b c]))

(rum/defcc comp-arglists-1
  ([comp a])
  ([comp a b])
  ([comp a b c]))

(deftest test-arglists
  (is (= (:arglists (meta #'comp-mixins))
         '([])))
  (is (= (:arglists (meta #'comp-arglists))
         '([a] [a b] [a b c])))
  (is (= (:arglists (meta #'comp-arglists-1))
         '([a] [a b] [a b c]))))

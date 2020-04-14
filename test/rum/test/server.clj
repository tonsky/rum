(ns rum.test.server
  (:require
    [rum.core :as rum]
    [clojure.test :refer [deftest is are testing]]))


(rum/defcs comp-mixins < (rum/local 7)
                         { :will-mount (fn [s] (assoc s ::key 1))}
  [state]
  [:div
    [:.local @(:rum/local state)]
    [:.key   (::key state)]])

(deftest test-120
  (is (= (rum/render-static-markup [:input {:type :checkbox}])
         "<input type=\"checkbox\"/>")))

(deftest test-lifecycle
  (is (= (comp-mixins)
         [:div
           [:.local 7]
           [:.key   1]])))


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

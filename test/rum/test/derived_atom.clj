(ns rum.test.derived-atom
  (:require
   [rum.core :as rum]
   [clojure.test :refer [deftest is are testing]]))

(deftest test-derived
  (let [*a (atom 0)
        *b (atom "x")
        *d (rum/derived-atom [*a *b] ::key
                             (fn [a b]
                               (str a ":" b)))]
    (is (= "0:x" @*d))
    (swap! *a inc)
    (is (= "1:x" @*d))
    (reset! *b "y")
    (is (= "1:y" @*d)))

  (testing "user-provided ref"
    (let [*a (atom 0)
          *d (atom nil)]
      (rum/derived-atom [*a] ::key str {:ref *d})
      (is (= "0" @*d))
      (swap! *a inc)
      (is (= "1" @*d))))

  (testing "check-equals"
    (let [*a (atom 0)
          *d (atom nil)
          *resets (atom 0)]
      (add-watch *d ::count-resets (fn [_ _ _ _] (swap! *resets inc)))
      (rum/derived-atom [*a] ::key #(mod % 10) {:ref *d})
      (is (= 0 @*d))
      (is (= 1 @*resets))
      (reset! *a 1)
      (is (= 1 @*d))
      (is (= 2 @*resets))
      (reset! *a 11)       ;; *a changes,
      (is (= 1 @*d))       ;; but *d does not
      (is (= 2 @*resets))) ;; should not register reset!

    (let [*a (atom 0)
          *d (atom nil)
          *resets (atom 0)]
      (add-watch *d ::count-resets (fn [_ _ _ _] (swap! *resets inc)))
      (rum/derived-atom [*a] ::key #(mod % 10) {:ref *d :check-equals? false})
      (is (= 0 @*d))
      (is (= 1 @*resets))
      (reset! *a 1)
      (is (= 1 @*d))
      (is (= 2 @*resets))
      (reset! *a 11)
      (is (= 1 @*d))
      (is (= 3 @*resets))))) ;; should register reset! anyways


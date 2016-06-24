(ns rum.test.cursor
  (:require
    [rum.core :as rum]
    [clojure.test :refer [deftest is are testing]]))


(deftest test-cursor
  (let [a   (atom { :b 1 :c { :cd 2 } })
        b   (rum/cursor a :b)
        c   (rum/cursor a :c)
        cd  (rum/cursor c :cd)
        ccd (rum/cursor-in a [:c :cd])]
    (testing "deref"
      (is (= 1 @b))
      (is (= { :cd 2} @c))
      (is (= 2 @cd))
      (is (= 2 @ccd)))
    
    (testing "swap!"
      (is (= 2 (swap! b inc)))
      (is (= 2 @b))
      (is (= 3 (swap! cd inc)))
      (is (= 3 @cd)))
      
    (testing "reset!"
      (is (= 4 (reset! b 4)))
      (is (= 4 @b))
      (is (= 5 (reset! cd 5)))
      (is (= 5 @cd)))
      
    (testing "compare-and-set!"
      (is (= true (compare-and-set! b 4 6)))
      (is (= 6 @b))
      (is (= false (compare-and-set! b 4 7)))
      (is (= 6 @b))
      (is (= true (compare-and-set! cd 5 8)))
      (is (= 8 @cd))
      (is (= false (compare-and-set! cd 5 9)))
      (is (= 8 @cd)))
    
    (testing "watches"
      (let [b-count  (atom 0)
            b-states (atom [])
            _        (add-watch b ::count (fn [_ _ _ _] (swap! b-count inc)))
            _        (add-watch b ::count (fn [_ _ _ _] (swap! b-count inc))) ;; duplicate add
            _        (add-watch b ::states (fn [_ _ o n] (swap! b-states conj [o n])))
            cd-count (atom 0)
            _        (add-watch cd ::count (fn [_ _ _ _] (swap! cd-count inc)))]
        
        (= 2 (count (.getWatches b)))
        (= 1 (count (.getWatches cd)))
        
        (swap! b inc)
        (is (= 1 @b-count))
        (is (= [[6 7]] @b-states))
        (is (= 0 @cd-count))
        
        (swap! cd inc)
        (is (= 1 @b-count))
        (is (= [[6 7]] @b-states))
        (is (= 1 @cd-count))
        
        (remove-watch b ::count)
        (= 1 (count (.getWatches b)))
        (swap! b inc)
        (is (= 1 @b-count))
        (is (= [[6 7] [7 8]] @b-states)))))
    
  (testing "meta"
    (let [c (rum/cursor (atom nil) :b :meta { :k 1 })]
      (is (= { :k 1 } (meta c)))
      (alter-meta! c update :k inc)
      (is (= { :k 2 } (meta c)))
      (reset-meta! c { :l 3 })
      (is (= { :l 3 } (meta c)))))
  
  (testing "vectors"
    (let [a (atom [1 [2 3] { :k 4 }])
          b (rum/cursor a 0)
          c (rum/cursor-in a [1 0])
          d (rum/cursor-in a [2 :k])]
      (is (= 1 @b))
      (is (= 2 @c))
      (is (= 4 @d))
      (swap! b inc)
      (swap! c inc)
      (swap! d inc)
      (is (= [2 [3 3] { :k 5 }] @a)))))
      

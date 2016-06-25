(ns rum.cursor)


(deftype Cursor [ref path meta]
  Object
  (equiv [this other]
    (-equiv this other))

  IAtom
  
  IMeta
  (-meta [_] meta)
  
  IEquiv
  (-equiv [this other]
    (identical? this other))

  IDeref
  (-deref [_]
    (get-in (-deref ref) path))

  IWatchable
  (-add-watch [this key callback]
    (add-watch ref (list this key)
      (fn [_ _ oldv newv]
        (let [old (get-in oldv path)
              new (get-in newv path)]
          (when (not= old new)
            (callback key this old new)))))
    this)
  
  (-remove-watch [this key]
    (remove-watch ref (list this key))
    this)

  IHash
  (-hash [this] (goog/getUid this))

  IReset
  (-reset! [_ newv]
    (swap! ref assoc-in path newv)
    newv)

  ISwap
  (-swap! [this f]
    (-reset! this (f (-deref this))))
  (-swap! [this f a]
    (-reset! this (f (-deref this) a)))
  (-swap! [this f a b]
    (-reset! this (f (-deref this) a b)))
  (-swap! [this f a b rest]
    (-reset! this (apply f (-deref this) a b rest)))
  
  IPrintWithWriter
  (-pr-writer [this writer opts]
    (-write writer "#object [rum.cursor.Cursor ")
    (pr-writer {:val (-deref this)} writer opts)
    (-write writer "]")))


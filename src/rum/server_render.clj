(ns rum.server-render
  (:require
    [clojure.string :as str])
  (:import
    [clojure.lang IPersistentVector ISeq Named Numbers Ratio Keyword]))


(defn append!
  ([^StringBuilder sb s0] (.append sb s0))
  ([^StringBuilder sb s0 s1]
    (.append sb s0)
    (.append sb s1))
  ([^StringBuilder sb s0 s1 s2]
    (.append sb s0)
    (.append sb s1)
    (.append sb s2))
  ([^StringBuilder sb s0 s1 s2 s3]
    (.append sb s0)
    (.append sb s1)
    (.append sb s2)
    (.append sb s3))
  ([^StringBuilder sb s0 s1 s2 s3 s4]
    (.append sb s0)
    (.append sb s1)
    (.append sb s2)
    (.append sb s3)
    (.append sb s4)))


(defprotocol ToString
  (^String to-str [x] "Convert a value into a string."))


(extend-protocol ToString
  Keyword (to-str [k] (name k))
  Ratio   (to-str [r] (str (float r)))
  String  (to-str [s] s)
  Object  (to-str [x] (str x))
  nil     (to-str [_] ""))
  

(def ^{:doc "A list of elements that must be rendered without a closing tag."
       :private true}
  void-tags
  #{"area" "base" "br" "col" "command" "embed" "hr" "img" "input" "keygen" "link"
    "meta" "param" "source" "track" "wbr"})


(defn container-tag? [tag content]
  (or content
      (not (contains? void-tags tag))))


(def attr-mapping
  {:class-name "class"
   :className  "class"
   :html-for   "for"
   :htmlFor    "for"})


(defn react-attr->html ^String [attr]
  (let [^String attr-str (or (attr-mapping attr)
                             (str/lower-case (to-str attr)))]
    (if (or (.startsWith attr-str "data-")
            (.startsWith attr-str "aria-")
            (.startsWith attr-str "-"))
      attr-str
      (str/replace attr-str "-" ""))))


(defn escape-html [^String s]
  (let [len (count s)]
    (loop [^StringBuilder sb nil
           i                 (int 0)]
      (if (< i len)
        (let [char (.charAt s i)
              repl (case char
                     \& "&amp;"
                     \< "&lt;"
                     \> "&gt;"
                     \" "&quot;"
                     \' "&#x27;"
                     nil)]
          (if (nil? repl)
            (if (nil? sb)
              (recur nil (inc i))
              (recur (doto sb
                       (.append char))
                     (inc i)))
            (if (nil? sb)
              (recur (doto (StringBuilder.)
                       (.append s 0 i)
                       (.append repl))
                     (inc i))
              (recur (doto sb
                       (.append repl))
                     (inc i)))))
        (if (nil? sb) s (str sb))))))
        

(defn normalize-attrs [attrs]
  (->> (for [[k v] attrs
             :let [k (react-attr->html k)]
             :when (not (.startsWith k "on"))]
         [(keyword k) v])
       (into {})))


(defn merge-attrs
  [tag-attrs attrs]
  (let [attrs       (normalize-attrs attrs)
        tag-class   (:class tag-attrs)
        attrs-class (:class attrs)
        class       (if (and tag-class attrs-class)
                      [tag-class attrs-class]
                      (or tag-class attrs-class))]
    (-> (into (dissoc tag-attrs :class)
              (dissoc attrs :class))
        (assoc :class class))))


(defn match-tag
  "Match `s` as a CSS tag and return a vector of tag name, CSS id and
  CSS classes."
  [s]
  (let [matches (re-seq #"[#.]?[^#.]+" (name s))
        [tag-name names] (cond 
                           (empty? matches)
                             (throw (ex-info (str "Can't match CSS tag: " s) {:tag s}))
                           (#{\# \.} (ffirst matches)) ;; shorthand for div
                             ["div" matches]
                           :default
                             [(first matches) (rest matches)])]
    [tag-name
     (->> names
          (filter #(.startsWith ^String % "#"))
          (map #(subs % 1))
          first)
     (->> names
          (filter #(.startsWith ^String % "."))
          (mapv #(subs % 1)))]))


(defn normalize-element
  "Ensure an element vector is of the form [tag-name attrs content]."
  [[tag & content]]
  (when (not (or (keyword? tag) (symbol? tag) (string? tag)))
    (throw (IllegalArgumentException. (str tag " is not a valid element name."))))
  (let [[tag id classes] (match-tag tag)
        tag-attrs        { :id id
                           :class classes }
        map-attrs        (first content)]
    (cond
      (map? map-attrs)
        [tag (merge-attrs tag-attrs map-attrs) (next content)]
      (nil? map-attrs)
        [tag tag-attrs (next content)]
      :else
        [tag tag-attrs content])))


;;; render attributes


;; https://github.com/facebook/react/blob/master/src/renderers/dom/shared/CSSProperty.js
(def unitless-css-props
  (into #{}
    (for [key ["animation-iteration-count" "box-flex" "box-flex-group" "box-ordinal-group" "column-count" "flex" "flex-grow" "flex-positive" "flex-shrink" "flex-negative" "flex-order" "grid-row" "grid-column" "font-weight" "line-clamp" "line-height" "opacity" "order" "orphans" "tab-size" "widows" "z-index" "zoom" "fill-opacity" "stop-opacity" "stroke-dashoffset" "stroke-opacity" "stroke-width"]
          prefix ["" "-webkit-" "-ms-" "-moz-" "-o-"]]
      (str prefix key))))


(defn normalize-css-key [k]
  (-> (to-str k)
      (str/replace #"[A-Z]" (fn [ch] (str "-" (str/lower-case ch))))
      (str/replace #"^ms-" "-ms-")))


(defn normalize-css-value [key value]
  (cond
    (contains? unitless-css-props key)
      (escape-html (to-str value))
    (number? value)
      (str value (when (not= 0 value) "px"))
    (and (string? value)
         (re-matches #"\s*\d+\s*" value))
      (recur key (-> value str/trim Long/parseLong))
    :else
      (escape-html (to-str value))))


(defn render-style-kv! [sb empty? k v]
  (if v
    (do
      (when empty?
        (append! sb " style=\""))
      (let [key (normalize-css-key k)
            val (normalize-css-value key v)]
        (append! sb key ":" val ";"))
      false)
    empty?))


(defn render-style! [map sb]
  (let [empty? (reduce-kv (partial render-style-kv! sb) true map)]
    (when-not empty?
      (append! sb "\""))))


(defn render-class! [sb empty? class]
  (cond
    (string? class)
      (do
        (if empty?
          (append! sb " class=\"" class)
          (append! sb " " class))
        false)
    (or (sequential? class)
        (set? class))
      (reduce (partial render-class! sb) empty? class)
    :else
      (render-class! sb empty? (to-str class))))


(defn render-classes! [classes sb]
  (let [empty? (render-class! sb true classes)]
    (when-not empty?
      (append! sb "\""))))


(defn render-attr! [name value sb]
  (let [name (to-str name)]
    (cond
      (true? value)    (append! sb " " name "=\"\"")
      (not value)      :nop
      (= "style" name) (render-style! value sb)
      (= "class" name) (render-classes! value sb)
      :else            (append! sb " " name "=\"" (to-str value) "\""))))


(defn render-attrs! [attrs sb]
  (reduce-kv (fn [_ k v] (render-attr! k v sb)) nil attrs))


;;; render html


(defprotocol HtmlRenderer
  (-render-html [this parent *key sb]
    "Turn a Clojure data type into a string of HTML with react ids."))


(defn -render-element
  "Render an element vector as a HTML element."
  [element *key sb]
  (let [[tag attrs content] (normalize-element element)
        key                 @*key
        _                   (vswap! *key inc)
        attrs               (cond-> attrs
                              true       (dissoc :key)
                              (== key 1) (assoc  :data-reactroot "")
                              true       (assoc  :data-reactid key))
        inner-html          (:dangerouslysetinnerhtml attrs)
        attrs               (if inner-html
                              (dissoc attrs :dangerouslysetinnerhtml)
                              attrs)]

    (when inner-html
      (when-not (empty? content)
        (throw (Exception. "Invariant Violation: Can only set one of `children` or `props.dangerouslySetInnerHTML`.")))
      (when-not (:__html inner-html)
        (throw (Exception. "Invariant Violation: `props.dangerouslySetInnerHTML` must be in the form `{__html: ...}`. Please visit https://fb.me/react-invariant-dangerously-set-inner-html for more information."))))
    
    (append! sb "<" tag)
    (render-attrs! attrs sb)
    
    (if (container-tag? tag content)
      (do
        (append! sb ">")
        (if inner-html
          (append! sb (:__html inner-html))
          (-render-html content element *key sb))
        (append! sb "</" tag ">"))
      (append! sb "/>"))))


(extend-protocol HtmlRenderer
  IPersistentVector
  (-render-html [this parent *key sb]
    (-render-element this *key sb))

  ISeq
  (-render-html [this parent *key sb]
    (doseq [element this]
      (-render-html element this *key sb)))

  Named
  (-render-html [this parent *key sb]
    (append! sb (name this)))

  String
  (-render-html [this parent *key sb]
    (if (> (count parent) 1)
      (let [key @*key]
        (vswap! *key inc)
        (append! sb "<!-- react-text: " key " -->" (escape-html this) "<!-- /react-text -->"))
      (append! sb (escape-html this))))

  Object
  (-render-html [this parent *key sb]
    (-render-html (str this) parent *key sb))

  nil
  (-render-html [this parent *key sb]
    :nop))


;; https://github.com/facebook/react/blob/master/src/shared/utils/adler32.js
(defn adler32 [^StringBuilder sb]
  (let [l (.length sb)
        m (bit-and l -4)]
    (loop [a (int 1)
           b (int 0)
           i 0
           n (min (+ i 4096) m)]
     (cond
       (< i n)
       (let [c0 (int (.charAt sb i))
             c1 (int (.charAt sb (+ i 1)))
             c2 (int (.charAt sb (+ i 2)))
             c3 (int (.charAt sb (+ i 3)))
             b  (+ b a c0 
                     a c0 c1
                     a c0 c1 c2
                     a c0 c1 c2 c3)
             a  (+ a c0 c1 c2 c3)]
         (recur (rem a 65521) (rem b 65521) (+ i 4) n))
      
       (< i m)
       (recur a b i (min (+ i 4096) m))
      
       (< i l)
       (let [c0 (int (.charAt sb i))]
         (recur (+ a c0) (+ b a c0) (+ i 1) n))
      
       :else
       (let [a (rem a 65521)
             b (rem b 65521)]
         (bit-or (int a) (Numbers/shiftLeftInt b 16)))))))


(defn render-html
  ([src] (render-html src nil))
  ([src opts]
    (let [sb (StringBuilder.)]
      (-render-html src nil (volatile! 1) sb)
      (.insert sb (.indexOf sb ">") (str " data-react-checksum=\"" (adler32 sb) "\""))
      (str sb))))

(render-html [:div { :class [nil] }])

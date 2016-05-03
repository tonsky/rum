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


(def attr-mapping
  { :class-name "class"
    :className  "class"
    :html-for   "for"
    :htmlFor    "for" })


(defn normalize-attr-key ^String [key]
  (let [^String str (or (attr-mapping key)
                        (str/lower-case (to-str key)))]
    (if (or (.startsWith str "data-")
            (.startsWith str "aria-")
            (.startsWith str "-"))
      str
      (str/replace str "-" ""))))


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
        

(defn parse-selector [s]
  (loop [matches (re-seq #"([#.])?([^#.]+)" (name s))
         tag     "div"
         id      nil
         classes nil]
    (if-let [[_ prefix val] (first matches)]
      (case prefix
        nil (recur (next matches) val id classes)
        "#" (recur (next matches) tag val classes)
        "." (recur (next matches) tag id (conj (or classes []) val)))
      [tag id classes])))


(defn normalize-element [[first second & rest]]
  (when-not (or (keyword? first)
                (symbol? first)
                (string? first))
    (throw (ex-info "Expected a keyword as a tag" { :tag first })))
  (let [[tag tag-id tag-classes] (parse-selector first)
        [attrs children] (if (or (map? second)
                                 (nil? second))
                           [second rest]
                           [nil    (cons second rest)])
        attrs-classes    (or (:class attrs)
                             (:class-name attrs)
                             (:className attrs))
        classes          (if (and tag-classes attrs-classes)
                           [tag-classes attrs-classes]
                           (or tag-classes attrs-classes))]
    [tag tag-id classes attrs children]))


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
    (and (string? value)
         (re-matches #"\s*\d+\.\d+\s*" value))
      (recur key (-> value str/trim Double/parseDouble))
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
  (when classes
    (let [empty? (render-class! sb true classes)]
      (when-not empty?
        (append! sb "\"")))))


(defn render-attr! [key value sb]
  (let [attr (normalize-attr-key key)]
    (cond
      (= "style" attr) (render-style! value sb)
      (= "key" attr)   :nop
      (= "class" attr) :nop
      (not value)      :nop
      (true? value)    (append! sb " " attr "=\"\"")
      (.startsWith attr "on")            :nop
      (= "dangerouslysetinnerhtml" attr) :nop
      :else            (append! sb " " attr "=\"" (to-str value) "\""))))


(defn render-attrs! [attrs sb]
  (reduce-kv (fn [_ k v] (render-attr! k v sb)) nil attrs))


;;; render html


(defprotocol HtmlRenderer
  (-render-html [this parent *key sb]
    "Turn a Clojure data type into a string of HTML with react ids."))


(deftype HtmlRendered [^StringBuilder sb]
  Object
  (toString [_] (str sb))
  ToString
  (to-str [_] (str sb)))


(defn render-inner-html! [attrs children sb]
  (when-let [inner-html (:dangerouslySetInnerHTML attrs)]
    (when-not (empty? children)
      (throw (Exception. "Invariant Violation: Can only set one of `children` or `props.dangerouslySetInnerHTML`.")))
    (when-not (:__html inner-html)
      (throw (Exception. "Invariant Violation: `props.dangerouslySetInnerHTML` must be in the form `{__html: ...}`. Please visit https://fb.me/react-invariant-dangerously-set-inner-html for more information.")))
    (append! sb (:__html inner-html))
    true))

  
(defn render-content! [tag attrs children *key sb]
  (if (and (nil? children)
           (contains? void-tags tag))
    (append! sb "/>")
    (do
      (append! sb ">")
      (or (render-inner-html! attrs children sb)
          (doseq [element children]
            (-render-html element children *key sb)))
      (append! sb "</" tag ">"))))


(defn render-element!
  "Render an element vector as a HTML element."
  [element *key sb]
  (let [[tag id classes attrs children] (normalize-element element)]

    (append! sb "<" tag)
    
    (when id
      (append! sb " id=\"" id "\""))
    
    (render-attrs! attrs sb)
    
    (render-classes! classes sb)
    
    (when *key
      (when (== @*key 1)
        (append! sb " data-reactroot=\"\""))

      (append! sb " data-reactid=\"" @*key "\"")
      (vswap! *key inc))
    
    (render-content! tag attrs children *key sb)))


(extend-protocol HtmlRenderer
  IPersistentVector
  (-render-html [this parent *key sb]
    (render-element! this *key sb))

  ISeq
  (-render-html [this parent *key sb]
    (doseq [element this]
      (-render-html element parent *key sb)))

  Named
  (-render-html [this parent *key sb]
    (append! sb (name this)))

  String
  (-render-html [this parent *key sb]
    (if (and *key
             (> (count parent) 1))
      (let [key @*key]
        (vswap! *key inc)
        (append! sb "<!-- react-text: " key " -->" (escape-html this) "<!-- /react-text -->"))
      (append! sb (escape-html this))))

  HtmlRendered
  (-render-html [this _ _ sb]
    (append! sb (str this)))

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
      (HtmlRendered. sb))))

(defn render-html-str
  [& xs]
  (str (apply render-html xs)))


(defn render-static-markup [src]
  (let [sb (StringBuilder.)]
    (-render-html src nil nil sb)
    (HtmlRendered. sb)))

(defn render-static-markup-str [src]
  (str (render-static-markup src)))

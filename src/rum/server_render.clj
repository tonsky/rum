(ns rum.server-render
  (:require
    [clojure.string :as str])
  (:import
    [clojure.lang IPersistentVector ISeq Named Numbers Ratio Keyword]))

;; From Weavejester's Hiccup
;; https://github.com/weavejester/hiccup/blob/master/src/hiccup/compiler.clj#L32
(def ^{:doc "Regular expression that parses a CSS-style id and class from a tag name."
       :private true}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")


(def ^{:doc "A list of elements that must be rendered without a closing tag."
       :private true}
  void-tags
  #{"area" "base" "br" "col" "command" "embed" "hr" "img" "input" "keygen" "link"
    "meta" "param" "source" "track" "wbr"})


;;; to-str

(defprotocol ToString
  (^String to-str [x] "Convert a value into a string."))


(extend-protocol ToString
  Keyword
  (to-str [k] (name k))
  Ratio
  (to-str [r] (str (float r)))
  String
  (to-str [s] s)
  Object
  (to-str [x] (str x))
  nil
  (to-str [_] ""))


(defn ^String as-str
  "Converts its arguments into a string using to-str."
  [& xs]
  (apply str (map to-str xs)))


(defn container-tag? [tag content]
  (or content (not (void-tags tag))))


;;; other

(def attr-mapping
  {:class-name "class"
   :className  "class"
   :html-for   "for"
   :htmlFor    "for"})


(defn react-attr->html ^String [attr]
  (-> (or (attr-mapping attr)
          (as-str attr))
    (str/lower-case)
    (str/replace "-" "")))


(defn escape-html
  "Change special characters into HTML character entities."
  [^String text]
  (.. ^String text
    (replace "&"  "&amp;")
    (replace "<"  "&lt;")
    (replace ">"  "&gt;")
    (replace "\"" "&quot;")
    (replace "'"  "&#x27;")))


(defn merge-attrs
  [tag-attrs attrs]
  (let [attrs       (->> (for [[k v] attrs
                               :let [k (react-attr->html k)]
                               :when (not (.startsWith k "on"))]
                           [(keyword k) v])
                      (into {}))
        tag-class   (:class tag-attrs)
        attrs-class (:class attrs)
        class       (if (and tag-class attrs-class)
                      [tag-class attrs-class]
                      (or tag-class attrs-class))]
    (-> tag-attrs
      (into attrs)
      (dissoc :class)
      (assoc :class class))))


(defn strip-css
  "Strip the # and . characters from the beginning of `s`."
  [s] (if s (str/replace s #"^[.#]" "")))


(defn match-tag
  "Match `s` as a CSS tag and return a vector of tag name, CSS id and
  CSS classes."
  [s]
  (let [matches (re-seq #"[#.]?[^#.]+" (name s))
        [tag-name names]
        (cond (empty? matches)
              (throw (ex-info (str "Can't match CSS tag: " s) {:tag s}))
              (#{\# \.} (ffirst matches)) ;; shorthand for div
              ["div" matches]
              :default
              [(first matches) (rest matches)])]
    [tag-name
     (first (map strip-css (filter #(= \# (first %1)) names)))
     (vec (map strip-css (filter #(= \. (first %1)) names)))]))


(defn normalize-element
  "Ensure an element vector is of the form [tag-name attrs content]."
  [[tag & content]]
  (when (not (or (keyword? tag) (symbol? tag) (string? tag)))
    (throw (IllegalArgumentException. (str tag " is not a valid element name."))))
  (let [[tag id classes] (match-tag tag)
        tag-attrs        {:id    id
                          :class classes}
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
  (-> (as-str k)
      (str/replace #"[A-Z]" (fn [ch] (str "-" (str/lower-case ch))))
      (str/replace #"^ms-" "-ms-")))


(defn normalize-css-value [key value]
  (cond
    (contains? unitless-css-props key)
      (escape-html (as-str value))
    (number? value)
      (str value "px")
    (and (string? value)
         (re-matches #"\s*(\d+)\s*" value))
      (recur key (-> value str/trim Long/parseLong))
    :else
      (escape-html (as-str value))))


(defn render-style [value]
  (->> (for [[k v] value
             :when v
             :let [key   (normalize-css-key k)
                   value (normalize-css-value key v)]]
         (str key ":" value ";"))
       (str/join "")))


(defn normalize-classes* [cs]
  (cond 
    (sequential? cs) (mapcat normalize-classes* cs)
    (set? cs)        (mapcat normalize-classes* cs)
    :else            [(as-str cs)]))

  
(defn normalize-classes [cs]
  (when (or (string? cs)
            (not-empty cs))
    (when-let [cs (not-empty (normalize-classes* cs))]
      (str/join " " (dedupe cs)))))


(defn render-attr [[name value]]
  (let [name (as-str name)]
    (cond
      (true? value)    (str " " name)
      (not value)      ""
      (= "style" name) (str " style=\"" (render-style value) "\"")
      (= "class" name) (when-let [value (normalize-classes value)]
                         (str " class=\"" value "\""))
      :else            (str " " name "=\"" (as-str value) "\""))))


(defn render-attr-map [attrs]
  (->> attrs
    (map render-attr)
    (str/join "")))


(defn react-key
  "React escapes some characters in key"
  [^String text]
  (.. text
    (replace "=" "=0")
    (replace ":" "=2")))


;;; render html


(defprotocol HtmlRenderer
  (-render-html [this parent path]
    "Turn a Clojure data type into a string of HTML with react ids."))


;; https://github.com/facebook/react/blob/v0.14.7/src/renderers/shared/reconciler/ReactInstanceHandles.js
(defn render-reactid [path]
  (->> path
       (map #(if (number? %) (Long/toString % 36) %))
       (str/join "")))


(defn -render-element
  "Render an element vector as a HTML element."
  [element path]
  (let [[tag attrs content] (normalize-element element)
        path                (if (:key attrs)
                              (conj (pop path) "$" (react-key (to-str (:key attrs))))
                              path)
        attrs               (assoc attrs
                              :key nil
                              :data-reactid (render-reactid path))]
    (if (container-tag? tag content)
      (str "<" tag (render-attr-map attrs) ">"
           (-render-html content element path)
           "</" tag ">")
      (str "<" tag (render-attr-map attrs) "/>"))))


(extend-protocol HtmlRenderer
  IPersistentVector
  (-render-html [this parent path]
    (-render-element this path))
  ISeq
  (-render-html [this parent path]
    (let [separator (if (or (vector? parent) (= (list this) parent)) "." ":")
          path      (if (= (list this) parent) (-> path pop pop) path)]
      (->> this
        (filter identity)
        (map-indexed #(-render-html %2 this (conj path separator %1)))
        (apply str))))
  Named
  (-render-html [this parent path]
    (name this))
  String
  (-render-html [this parent path]
    (if (> (count parent) 1)
      (-render-html [:span this] parent path)
      (escape-html this)))
  Object
  (-render-html [this parent path]
    (-render-html (str this) parent path))
  nil
  (-render-html [this parent path]
    ""))


;; https://github.com/facebook/react/blob/master/src/shared/utils/adler32.js
(defn adler32 [^String s]
  (let [l (count s)
        m (bit-and l -4)]
    (loop [a (int 1)
           b (int 0)
           i 0
           n (min (+ i 4096) m)]
     (cond
       (< i n)
       (let [c0 (int (.charAt s i))
             c1 (int (.charAt s (+ i 1)))
             c2 (int (.charAt s (+ i 2)))
             c3 (int (.charAt s (+ i 3)))
             b  (+ b a c0 
                     a c0 c1
                     a c0 c1 c2
                     a c0 c1 c2 c3)
             a  (+ a c0 c1 c2 c3)]
         (recur (rem a 65521) (rem b 65521) (+ i 4) n))
      
       (< i m)
       (recur a b i (min (+ i 4096) m))
      
       (< i l)
       (let [c0 (int (.charAt s i))]
         (recur (+ a c0) (+ b a c0) (+ i 1) n))
      
       :else
       (let [a (rem a 65521)
             b (rem b 65521)]
         (bit-or (int a) (Numbers/shiftLeftInt b 16)))))))


(defn render-html
  ([src] (render-html src nil))
  ([src opts]
    (let [result   (-render-html src nil ["." (:root-key opts 0)])
          checksum (adler32 result)]
      (str/replace-first result ">"
        (str " data-react-checksum=\"" checksum "\">")))))

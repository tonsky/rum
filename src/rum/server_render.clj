(ns rum.server-render
  (:require [clojure.string :as str])
  (:import [clojure.lang IPersistentVector ISeq Named]
           [rum Adler32]))

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
  clojure.lang.Keyword
  (to-str [k] (name k))
  clojure.lang.Ratio
  (to-str [r] (str (float r)))
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
  {:class-name :class
   :className :class
   :html-for :for
   :htmlFor :for})

(defn react-attr->html [attr]
  (-> (attr-mapping attr attr)
    as-str
    .toLowerCase
    (.replace "-" "")))

(defn merge-attrs
  [tag-attrs attrs]
  (let [attrs       (->> (for [[k v] attrs
                               :let [k (react-attr->html k)]
                               :when (not (.startsWith ^String k "on"))]
                           [(keyword k) v])
                      (into {}))
        tag-class   (:class tag-attrs)
        attrs-class (:class attrs)
        class       (if (and tag-class attrs-class)
                      (str tag-class " " attrs-class)
                      (or tag-class attrs-class))]
    (-> tag-attrs
      (into attrs)
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
                          :class (when-not (empty? classes)
                                   (str/join " " classes))}
        map-attrs        (first content)]
    (if (map? map-attrs)
      [tag (merge-attrs tag-attrs map-attrs) (next content)]
      [tag tag-attrs content])))

;;; render attributes

(defn render-style [value]
  (str/join " "
    (for [[k v] value
          :when v]
      (str (as-str k) ":" (as-str v) ";"))))

(defn attr-value [name value]
  (condp = (as-str name)
    "style" (render-style value)
            (as-str value)))

(defn render-attr [[name value]]
  (cond
    (true? value) (str " " (as-str name))
    (not value)   ""
    :else         (str " " (as-str name) "=\""
                    (attr-value name value) "\"")))

(def attr->rank
  {:id     0
   :shape  2
   :coords 3
   :href   10
   :style  20
   :alt    30
   :rel    35
   :target 40
   :src    45
   :usemap 46
   :class  50
   :name   60})

(defn rank-attr [[attr value]]
  [(attr->rank attr 99) attr])

(defn render-attr-map [attrs]
  (->> attrs
    (sort-by rank-attr)
    (map render-attr)
    (apply str)))

(defn escape-html
  "Change special characters into HTML character entities."
  [^String text]
  (.. ^String text
    (replace "&"  "&amp;")
    (replace "<"  "&lt;")
    (replace ">"  "&gt;")
    (replace "\"" "&quot;")
    (replace "'"  "&#x27;")))

(defn react-key
  "React escapes some characters in key"
  [^String text]
  (.. ^String text
    (replace "=" "=0")
    (replace ":" "=2")))

;;; render html

(defprotocol HtmlRenderer
  (-render-html [this parent path]
    "Turn a Clojure data type into a string of HTML with react ids."))

(defn -render-element
  "Render an element vector as a HTML element."
  [element path]
  (let [[tag attrs content] (normalize-element element)
        path                (if (:key attrs)
                              (conj (pop path) "$" (react-key (:key attrs)))
                              path)
        attrs               (assoc attrs
                              :key nil
                              :data-reactid (str/join "" path))]
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
    ;; (println "============")
    ;; (println "PATH  " (str/join "" path))
    ;; (println "PARENT" parent (not (vector? parent)))
    ;; (println "THIS  " this (= (list this) parent))
    (let [separator (if (or (vector? parent) (= (list this) parent)) "." ":")
          path      (if (= (list this) parent) (-> path pop pop) path)]
      (->> this
        (map-indexed #(-render-html %2 this (conj path separator %1)))
        (apply str))))
  Named
  (-render-html [this parent path]
    (name this))
  String
  (-render-html [this parent path]
    (escape-html this))
  Object
  (-render-html [this parent path]
    (str this))
  nil
  (-render-html [this parent path]
    ""))

(defn render-html [src]
  (let [result   (-render-html src nil ["." 0])
        checksum (Adler32/calc result)]
    (str/replace-first result ">"
      (str " data-react-checksum=\"" checksum "\">"))))

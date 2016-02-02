(ns rum.server-render
  (:require [clojure.string :as str])
  (:import [clojure.lang IPersistentVector ISeq Named]
           [java.util.zip Adler32]))

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

;;; adler32

(defn adler32 [value]
  (-> (doto (Adler32.)
        (.update (.getBytes ^String value)))
    .getValue))

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
    (.replace "-" "")))

(defn merge-attrs
  [tag-attrs attrs]
  (let [attrs (for [[k v] attrs
                    :let [k (react-attr->html k)]
                    :when (not (.startsWith ^String k "on"))]
                [k v])
        class (if (:class tag-attrs)
                (str (:class tag-attrs) " " (:class attrs))
                (:class attrs))]
    (-> tag-attrs
        (into attrs)
        (assoc :class class))))

(defn normalize-element
  "Ensure an element vector is of the form [tag-name attrs content]."
  [[tag & content]]
  (when (not (or (keyword? tag) (symbol? tag) (string? tag)))
    (throw (IllegalArgumentException. (str tag " is not a valid element name."))))
  (let [[_ tag id class] (re-matches re-tag (name tag))
        tag-attrs        {"id"    id
                          "class" (if class (.replace ^String class "." " "))}
        map-attrs        (first content)]
    (if (map? map-attrs)
      [tag (merge-attrs tag-attrs map-attrs) (next content)]
      [tag tag-attrs content])))

;;; render attributes

(defn render-style [value]
  (apply str
    (for [[k v] value
          :when v]
      (str (as-str k) "=" (as-str v) "; "))))

(defn attr-value [name value]
  (condp = (as-str name)
    "style" (render-style value)
            (as-str value)))

(defn render-attribute [[name value]]
  (cond
    (true? value) (str " " (as-str name))
    (not value)   ""
    :else         (str " " (as-str name) "=\""
                    (attr-value name value) "\"")))

(defn render-attr-map [attrs]
  (apply str
    (sort (map render-attribute attrs))))

;;; render html

(defprotocol HtmlRenderer
  (-render-html [this parent path]
    "Turn a Clojure data type into a string of HTML with react ids."))

(defn -render-element
  "Render an element vector as a HTML element."
  [element path]
  (let [[tag attrs content] (normalize-element element)
        path                (if (attrs "key")
                              (conj (subvec path 0 (dec (count path)))
                                "$" (attrs "key"))
                              path)
        attrs               (assoc attrs "key" nil
                              "data-reactid" (str/join "" path))]
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
    (if (= 1 (count this))
      (-render-html (first this) this path)
      (let [separator (if (and (not (vector? parent))
                               (not= this (first parent)))
                        ":" ".")]
        (->> this
          (map-indexed #(-render-html %2 this (conj path separator %1)))
          (apply str)))))
  Named
  (-render-html [this parent path]
    (name this))
  String
  (-render-html [this parent path]
    this)
  Object
  (-render-html [this parent path]
    (str this))
  nil
  (-render-html [this parent path]
    ""))

(defn render-html [src]
  (let [result   (-render-html src nil ["." 0])
        checksum (adler32 result)]
    (str/replace-first result ">"
      (str " data-react-checksum=\"" checksum "\">"))))

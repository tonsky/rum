(ns rum.server-render
  (:require [clojure.string :as str])
  (:import [clojure.lang IPersistentVector ISeq Named]))

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
    (.replace "-" "")))

(defn merge-attrs
  [tag-attrs attrs]
  (let [attrs (for [[k v] attrs
                    :let [k (react-attr->html k)]
                    :when (not (.startsWith k "on"))]
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
        tag-attrs        {:id id
                          :class (if class (.replace ^String class "." " "))}
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
  (render-html [this]
    "Turn a Clojure data type into a string of HTML."))

(defn- render-element
  "Render an element vector as a HTML element."
  [element]
  (let [[tag attrs content] (normalize-element element)]
    (if (container-tag? tag content)
      (str "<" tag (render-attr-map attrs) ">"
           (render-html content)
           "</" tag ">")
      (str "<" tag (render-attr-map attrs) "/>"))))

(extend-protocol HtmlRenderer
  IPersistentVector
  (render-html [this]
    (render-element this))
  ISeq
  (render-html [this]
    (apply str (map render-html this)))
  Named
  (render-html [this]
    (name this))
  String
  (render-html [this]
    this)
  Object
  (render-html [this]
    (str this))
  nil
  (render-html [this]
    ""))

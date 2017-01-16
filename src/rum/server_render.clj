(ns rum.server-render
  (:require
    [clojure.string :as str])
  (:import
    [clojure.lang IPersistentVector ISeq Named Numbers Ratio Keyword]))


(defn nothing? [element]
  (and (vector? element)
       (= :rum/nothing (first element))))


(def ^:dynamic *select-value*)

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



(def normalized-attrs
  { ;; special cases
    :default-checked "checked"
    :default-value "value"

    ;; https://github.com/facebook/react/blob/master/src/renderers/dom/shared/HTMLDOMPropertyConfig.js
    :accept-charset "accept-charset"
    :access-key "accesskey"
    :allow-full-screen "allowfullscreen"
    :allow-transparency "allowtransparency"
    :auto-complete "autocomplete"
    :auto-play "autoplay"
    :cell-padding "cellpadding"
    :cell-spacing "cellspacing"
    :char-set "charset"
    :class-id "classid"
    :col-span "colspan"
    :content-editable "contenteditable"
    :context-menu "contextmenu"
    :cross-origin "crossorigin"
    :date-time "datetime"
    :enc-type "enctype"
    :form-action "formaction"
    :form-enc-type "formenctype"
    :form-method "formmethod"
    :form-no-validate "formnovalidate"
    :form-target "formtarget"
    :frame-border "frameborder"
    :href-lang "hreflang"
    :http-equiv "http-equiv"
    :input-mode "inputmode"
    :key-params "keyparams"
    :key-type "keytype"
    :margin-height "marginheight"
    :margin-width "marginwidth"
    :max-length "maxlength"
    :media-group "mediagroup"
    :min-length "minlength"
    :no-validate "novalidate"
    :radio-group "radiogroup"
    :referrer-policy "referrerpolicy"
    :read-only "readonly"
    :row-span "rowspan"
    :spell-check "spellcheck"
    :src-doc "srcdoc"
    :src-lang "srclang"
    :src-set "srcset"
    :tab-index "tabindex"
    :use-map "usemap"
    :auto-capitalize "autocapitalize"
    :auto-correct "autocorrect"
    :auto-save "autosave"
    :item-prop "itemprop"
    :item-scope "itemscope"
    :item-type "itemtype"
    :item-id "itemid"
    :item-ref "itemref"
    
    ;; https://github.com/facebook/react/blob/master/src/renderers/dom/shared/SVGDOMPropertyConfig.js
    :allow-reorder "allowReorder"
    :attribute-name "attributeName"
    :attribute-type "attributeType"
    :auto-reverse "autoReverse"
    :base-frequency "baseFrequency"
    :base-profile "baseProfile"
    :calc-mode "calcMode"
    :clip-path-units "clipPathUnits"
    :content-script-type "contentScriptType"
    :content-style-type "contentStyleType"
    :diffuse-constant "diffuseConstant"
    :edge-mode "edgeMode"
    :external-resources-required "externalResourcesRequired"
    :filter-res "filterRes"
    :filter-units "filterUnits"
    :glyph-ref "glyphRef"
    :gradient-transform "gradientTransform"
    :gradient-units "gradientUnits"
    :kernel-matrix "kernelMatrix"
    :kernel-unit-length "kernelUnitLength"
    :key-points "keyPoints"
    :key-splines "keySplines"
    :key-times "keyTimes"
    :length-adjust "lengthAdjust"
    :limiting-cone-angle "limitingConeAngle"
    :marker-height "markerHeight"
    :marker-units "markerUnits"
    :marker-width "markerWidth"
    :mask-content-units "maskContentUnits"
    :mask-units "maskUnits"
    :num-octaves "numOctaves"
    :path-length "pathLength"
    :pattern-content-units "patternContentUnits"
    :pattern-transform "patternTransform"
    :pattern-units "patternUnits"
    :points-at-x "pointsAtX"
    :points-at-y "pointsAtY"
    :points-at-z "pointsAtZ"
    :preserve-alpha "preserveAlpha"
    :preserve-aspect-ratio "preserveAspectRatio"
    :primitive-units "primitiveUnits"
    :ref-x "refX"
    :ref-y "refY"
    :repeat-count "repeatCount"
    :repeat-dur "repeatDur"
    :required-extensions "requiredExtensions"
    :required-features "requiredFeatures"
    :specular-constant "specularConstant"
    :specular-exponent "specularExponent"
    :spread-method "spreadMethod"
    :start-offset "startOffset"
    :std-deviation "stdDeviation"
    :stitch-tiles "stitchTiles"
    :surface-scale "surfaceScale"
    :system-language "systemLanguage"
    :table-values "tableValues"
    :target-x "targetX"
    :target-y "targetY"
    :view-box "viewBox"
    :view-target "viewTarget"
    :x-channel-selector "xChannelSelector"
    :xlink-actuate "xlink:actuate"
    :xlink-arcrole "xlink:arcrole"
    :xlink-href "xlink:href"
    :xlink-role "xlink:role"
    :xlink-show "xlink:show"
    :xlink-title "xlink:title"
    :xlink-type "xlink:type"
    :xml-base "xml:base"
    :xmlns-xlink "xmlns:xlink"
    :xml-lang "xml:lang"
    :xml-space "xml:space"
    :y-channel-selector "yChannelSelector"
    :zoom-and-pan "zoomAndPan" })


(defn get-value [attrs]
  (or (:value attrs)
      (:default-value attrs)))


(defn normalize-attr-key ^String [key]
  (or (normalized-attrs key)
      (name key)))


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
        attrs-classes    (:class attrs)
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


(defn render-class! [sb first? class]
  (cond
    (nil? class)
      first?
    (string? class)
      (do
        (when-not first?
          (append! sb " "))
        (append! sb class)
        false)
    (or (sequential? class)
        (set? class))
      (reduce #(render-class! sb %1 %2) first? class)
    :else
      (render-class! sb first? (to-str class))))


(defn render-classes! [classes sb]
  (when classes
    (append! sb " class=\"")
    (render-class! sb true classes)
    (append! sb "\"")))


(defn- render-attr-str! [sb attr value]
  (append! sb " " attr "=\"" (escape-html (to-str value)) "\""))


(defn render-attr! [tag key value sb]
  (let [attr (normalize-attr-key key)]
    (cond
      (= "type" attr)  :nop ;; rendered manually in render-element! before id
      (= "style" attr) (render-style! value sb)
      (= "key" attr)   :nop
      (= "ref" attr)   :nop
      (= "class" attr) :nop
      (and (= "value" attr)
           (or (= "select" tag)
               (= "textarea" tag))) :nop
      (.startsWith attr "aria-") (render-attr-str! sb attr value)
      (not value)      :nop
      (true? value)    (append! sb " " attr "=\"\"")
      (.startsWith attr "on")            :nop
      (= "dangerouslySetInnerHTML" attr) :nop
      :else (render-attr-str! sb attr value))))


(defn render-attrs! [tag attrs sb]
  (reduce-kv (fn [_ k v] (render-attr! tag k v sb)) nil attrs))


;;; render html


(defprotocol HtmlRenderer
  (-render-html [this parent *key sb]
    "Turn a Clojure data type into a string of HTML with react ids."))


(defn render-inner-html! [attrs children sb]
  (when-let [inner-html (:dangerouslySetInnerHTML attrs)]
    (when-not (empty? children)
      (throw (Exception. "Invariant Violation: Can only set one of `children` or `props.dangerouslySetInnerHTML`.")))
    (when-not (:__html inner-html)
      (throw (Exception. "Invariant Violation: `props.dangerouslySetInnerHTML` must be in the form `{__html: ...}`. Please visit https://fb.me/react-invariant-dangerously-set-inner-html for more information.")))
    (append! sb (:__html inner-html))
    true))


(defn render-textarea-value! [tag attrs sb]
  (when (= tag "textarea")
    (when-some [value (get-value attrs)]
      (append! sb (escape-html value))
      true)))


(defn render-content! [tag attrs children *key sb]
  (if (and (nil? children)
           (contains? void-tags tag))
    (append! sb "/>")
    (do
      (append! sb ">")
      (or (render-textarea-value! tag attrs sb)
          (render-inner-html! attrs children sb)
          (doseq [element children]
            (-render-html element children *key sb)))
      (append! sb "</" tag ">"))))


(defn render-element!
  "Render an element vector as a HTML element."
  [element *key sb]
  (if (nothing? element)
    (when *key
      (let [key @*key]
        (vswap! *key inc)
        (append! sb "<!-- react-empty: " key " -->")))
    (let [[tag id classes attrs children] (normalize-element element)]
      (append! sb "<" tag)

      (when-some [type (:type attrs)]
        (append! sb " type=\"" type "\""))

      (when (and (= "option" tag)
                 (= (get-value attrs) *select-value*))
        (append! sb " selected=\"\""))

      (when id
        (append! sb " id=\"" id "\""))

      (render-attrs! tag attrs sb)

      (render-classes! classes sb)

      (when *key
        (when (== @*key 1)
          (append! sb " data-reactroot=\"\""))

        (append! sb " data-reactid=\"" @*key "\"")
        (vswap! *key inc))

      (if (= "select" tag)
        (binding [*select-value* (get-value attrs)]
          (render-content! tag attrs children *key sb))
        (render-content! tag attrs children *key sb)))))

        
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
      (when-not (nothing? src)
        (.insert sb (.indexOf sb ">") (str " data-react-checksum=\"" (adler32 sb) "\"")))
      (str sb))))


(defn render-static-markup [src]
  (let [sb (StringBuilder.)]
    (-render-html src nil nil sb)
    (str sb)))

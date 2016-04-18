(ns rum.perf
  (:require
    [rum.core :as rum]
    [clojure.string :as str]
    [net.cgrand.enlive-html :as enlive]
    [clojure.test :refer [deftest]]
    [criterium.core :as criterium]
    [hiccup.core :as hiccup]))


(def ^:dynamic *convert-style?* true)


(defn convert-tag-name [tag attrs]
  (let [id (:id attrs)
        classes (when-not (str/blank? (:class attrs))
                  (->> (str/split (:class attrs) #"\s+")
                       (remove str/blank?)))]
    (keyword
      (str tag
           (when id (str "#" id))
           (when-not (empty? classes)
             (str "." (str/join "." classes)))))))


(defn convert-style [s]
  (into {}
    (for [[_ k v] (re-seq #"([\w+\-]+)\s*:\s*([^;]+)" s)]
      (let [k' (keyword k)
            v' (condp re-matches v
                 #"(\d+)px"      :>> (fn [[_ n]] (Long/parseLong n))
                 #"(\d+\.\d+)px" :>> (fn [[_ n]] (Double/parseDouble n))
                 v)]
        [k' v']))))


(defn convert-attrs [attrs]
  (cond-> attrs
    true (dissoc :class :id :data-bem)
    (and *convert-style?*
         (contains? attrs :style)) (update :style convert-style)
    true not-empty))


(defn convert-tag [form]
  (cond
    ;; tag
    (map? form)
    (if (= :comment (:type form))
      nil
      (let [{:keys [tag attrs content type]} form
            tag'      (convert-tag-name (name tag) attrs)
            attrs'    (convert-attrs attrs)
            children  (->> (map convert-tag content)
                           (remove nil?))]
        (vec
          (concat [tag'] (when attrs' [attrs']) children))))
    
    ;; text node
    (string? form)
    (if (str/blank? form) nil form)))


(defn convert-page [page]
  (-> (slurp page)
      .getBytes
      java.io.ByteArrayInputStream.
      enlive/html-resource
      (enlive/select [:body])
      first
      convert-tag))


(defn file-size [path]
  (-> (count (slurp path)) (/ 1000) (long) (str " kB")))


(defn -main [& args]
  (doseq [page ["page1.html"
                "page2.html"
                "page3.html"]
          :let [path (str "perf/pages/" page)]]
    (let [comp (convert-page path)]
      (println "\n--- Testing" page (str "(" (file-size path) ")") "---")
      (criterium/quick-bench (rum/render-html comp)))
      
    (let [comp (binding [*convert-style?* false]
                 (convert-page path))]
      (println "\n+++ With Hiccup +++")
      (criterium/quick-bench (hiccup/html comp)))))

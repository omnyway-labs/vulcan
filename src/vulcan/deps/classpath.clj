(ns vulcan.deps.classpath
  (:require
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.java.io :as io]
   [me.raynes.fs :as fs]
   [vulcan.util :as u])
  (:import
   clojure.lang.RT))

(defn find-procurer [dep]
  (cond
    (some-> (:git/url dep)) :git
    (some-> (:mvn/version dep)) :jar))

(defn make-classpath [dep resource-paths]
  (condp = (find-procurer dep)
    :jar (:paths dep)
    :git (->> resource-paths
              (map #(format "%s/%s/" (:deps/root dep) %)))))

(defn make-all-classpath
  "Takes a list of resolved deps and paths
   returns a classpath list"
  [resolved-deps paths]
  (->> (vals resolved-deps)
      (map #(make-classpath % paths))
      flatten))

(defn dynamic-classloader []
  (.getParent (clojure.lang.RT/baseLoader)))

(defn get-classpath [classloader]
  (->> classloader
       (.getURLs)
       (map #(.getPath %))))

(defn current-classpath []
  (concat
   (get-classpath (ClassLoader/getSystemClassLoader))
   (get-classpath (dynamic-classloader))))

(defn add!
  ([path]
   (add! path (dynamic-classloader)))
  ([path classloader]
   (->> (java.io.File. path)
        (.toURL)
        (.addURL classloader))))

(defn add-all!
  ([paths]
   (add-all! paths (dynamic-classloader)))
  ([paths classloader]
   (let [classpath-set (set (current-classpath))]
     (doseq [path paths]
       (when-not (contains? classpath-set path)
         (add! path classloader))))))

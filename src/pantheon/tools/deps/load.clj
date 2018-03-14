(ns pantheon.tools.deps.load
  (:import
   (clojure.lang.RT)))

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

(defn add-classpath!
  ([path]
   (add-classpath! path (dynamic-classloader)))
  ([path classloader]
   (println "add-classpath!" path)
   (->> (java.io.File. path)
        (.toURL)
        (.addURL classloader))))

(defn load-paths!
  ([paths]
   (load-paths! paths (dynamic-classloader)))
  ([paths classloader]
   (let [classpath-set (set (current-classpath))]
     (doseq [path paths]
       (when-not (contains? classpath-set path)
         (add-classpath! path classloader))))))

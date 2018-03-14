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

(defn load-deps!
  ([deps]
   (load-deps! deps (dynamic-classloader)))
  ([deps classloader]
   (let [classpath-set (set (current-classpath))]
     (doseq [dep (vals deps)]
       (if-let [paths (not-empty (:paths dep))]
         (doseq [path paths]
           (when-not (contains? classpath-set path)
             (add-classpath! path classloader)))
         (when-let [path (not-empty (:deps/root dep))]
           (when-not (contains? classpath-set path)
             ;; fixme: get paths, extra-paths from deps.edn?
             (add-classpath! (str path "/src/") classloader)
             (add-classpath! (str path "/resources/") classloader))))))))

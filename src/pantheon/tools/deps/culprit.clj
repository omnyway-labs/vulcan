(ns pantheon.tools.deps.culprit
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.tools.deps.alpha :as deps])
  (:import
   [java.util.jar JarFile]
   [java.nio.file
    Files Path Paths FileVisitOption]))

(defn jar-files
  "Given the path to a .jar file on disk, return a seq of files contained in the zip"
  [path]
  (->> path
      (JarFile.)
      (.entries)
      (enumeration-seq)
      (map str)))

(defn jar-file? [path]
  (and (-> path io/file .exists)
       (-> path io/file .isFile)
       (boolean (re-find #".+\.jar$" path))))

(defn clj-file?
  "True if the path is a .clj file"
  [path]
  (re-find #"\.clj$" path))

(defn classfile? [path]
  (re-find #"\.class$" path))

;; Tweak of https://github.com/arohner/find-aot-dep
;; Make it tools.deps aware

(defn classfile-class
  "Given a path to a .class file, return the clj/java class it belongs to"
  [path]
  (-> path
      (#(re-find #"^([^$]*).*\.class$" %))
      second
      (str/replace "/" ".")
      (str/replace #"__init$" "")))

(defn clj-namespace [path]
  (-> (re-find #"^(.*)\.clj" path)
      second
      (str/replace "/" ".")))

(defn aot-clj?
  "True if the path represnts an AOT'd clojure namespace."
  [path]
  (re-find #"__init.class$" path))

(defn aot-clj-dep?
  "Returns a seq containing any AOT'd clojure namespaces in the jar that don't
  correspond to any .clj file in the jar"
  [jar-path]
  (let [files (jar-files jar-path)
        clj (->> files
                 (filter clj-file?)
                 (map clj-namespace)
                 (set))
        aot-clj (->> files
                     (filter aot-clj?)
                     (map classfile-class)
                     (set))]
    (seq (set/difference aot-clj clj))))

(defn offending-jars [jars]
  (doseq [jar jars
          :let [aot (aot-clj-dep? jar)]
          :when (seq aot)]
    (println jar "contains" aot)))

(defn jar? [dep-map]
  (some-> (:mvn/version dep-map)))

(defn find-aot-jars [deps]
  (->> (deps/resolve-deps
        {:deps  deps} nil)
       (map (fn [dep]
              (when (jar? dep)
                (:paths dep))))
       (offending-jars)))

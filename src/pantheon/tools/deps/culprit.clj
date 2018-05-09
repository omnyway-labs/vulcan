(ns pantheon.tools.deps.culprit
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [clojure.tools.deps.alpha :as deps])
  (:import
   [java.util.jar JarFile]
   [java.nio.file
    Files Path Paths FileVisitOption]))

(defn jar-files
  "Given the path to a .jar file on disk, return a seq of files contained in the zip"
  [path]
  (when path
    (->> path
       (JarFile.)
       (.entries)
       (enumeration-seq)
       (map str))))

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
        {:deps  deps
         :mvn/repos mvn/standard-repos} nil)
       (filter jar?)
       (map :paths)
       (offending-jars)))

;; Find overlapping namespaces

(def loaded-paths #"^(/src/|/test/)")

(def clj-extensions #".*\.(clj|cljc|cljs)$")

(defn is-omnyway-dep? [[dep-name _]]
  (= "omnypay" (namespace dep-name)))

(defn is-clojure-file? [file]
  (and (.isFile file)
       (re-find clj-extensions (.getName file))))

(defn truncate-root [root file]
  (str/replace-first (.getCanonicalPath file) root ""))

(defn is-in-loaded-paths? [path]
  "Relies on convention currently since we'd have to parse deps files to see
  what other paths would be, currently looks for src and test"
  (re-find loaded-paths path))


(defn relative-path->ns
  [path]
  (-> path
      (str/replace-first loaded-paths "")
      (str/replace #"\.(clj|cljs|cljc)$" "")
      (str/replace #"/" ".")
      (str/replace #"_" "-")
      symbol))

(defn dep->nses [dep-root]
  (->> dep-root
       clojure.java.io/file
       file-seq
       (filter is-clojure-file?)
       (map (partial truncate-root dep-root))
       (filter is-in-loaded-paths?)
       (map relative-path->ns)))

(defn add-current-project [deps-namespaces]
  (let [cwd (.getCanonicalPath (clojure.java.io/file "."))
        loaded-path-files (concat (file-seq (clojure.java.io/file "./src"))
                                  (file-seq (clojure.java.io/file "./test")))]
    (assoc deps-namespaces 'current-working-directory
           (->> loaded-path-files
                (filter is-clojure-file?)
                (map (partial truncate-root cwd))
                (map relative-path->ns)))))

(defn track-ns-occurrences [deps-with-nses]
  (reduce (fn [seen [dep nses]]
            (reduce (fn [seen ns]
                      (update seen ns (fnil conj []) dep))
                    seen
                    nses))
          {}
          deps-with-nses))

(defn duplicate-ns? [[_ deps]]
  (> (count deps) 1))

(defn report-duplicates [duplicate-nses]
  (doseq [[ns deps] duplicate-nses]
    (println "The following projects duplicate the namespace " ns ":")
    (println deps)))

(defn find-overlapping-namespaces [deps]
  (let [deps-namespaces (add-current-project
                         (into {}
                               (->> (deps/resolve-deps {:deps deps
                                                        :mvn/repos mvn/standard-repos} nil)
                                    (filter is-omnyway-dep?)
                                    (map (fn [[dep-name dep-val]]
                                           [dep-name (:deps/root dep-val)]))
                                    (map (fn [[dep-name dep-root]]
                                           [dep-name (dep->nses dep-root)])))))
        duplicate-nses (filter duplicate-ns? (track-ns-occurrences deps-namespaces))]
    (report-duplicates duplicate-nses)))

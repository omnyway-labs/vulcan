(ns pantheon.tools.deps.pack
  (:require
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [me.raynes.fs :as fs]
   [pantheon.tools.util :as u])
  (:import
   [clojure.lang ExceptionInfo]
   [java.io PushbackReader]))

(defn read-deps-file []
  (with-open [rdr (-> "deps.edn" io/reader (PushbackReader.))]
    (edn/read rdr)))

(defn git? [dep-map]
  (some-> (:git/url dep-map)))

(defn jar? [dep-map]
  (some-> (:mvn/version dep-map)))

(defn as-dep [[k v]]
  (assoc v :name
         (-> (name k)
             (str/split #"/")
             (last))))

(defn resolve-deps [deps]
  (->> (deps/resolve-deps
        {:deps      deps
         :mvn/repos mvn/standard-repos} nil)
       (map as-dep)))

(defn make-path [type path]
  (condp = type
    :git (format "lib/git/%s" path)
    :jar (format "lib/jar/%s.jar" (fs/name path))))

(defn cp-jars [{:keys [paths name]}]
  (fs/mkdirs "lib/jar")
  (doseq [path paths]
    (fs/copy path (make-path :jar path)))

(defn cp-git [dep]
  (fs/copy-dir (:deps/root dep)
               (make-path :git (:name dep)))))

(defn copy-dep [dep]
  (cond
    (git? dep) (cp-git dep)
    (jar? dep) (cp-jars dep)
    :else :nop))

(defn copy-deps [deps]
  (doseq [dep deps]
    (copy-dep dep)))
`

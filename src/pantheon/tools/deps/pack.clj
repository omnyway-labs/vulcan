(ns pantheon.tools.deps.pack
  (:require
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.java.io :as io]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [me.raynes.fs :as fs]
   [pantheon.tools.util :as u])
  (:import
   [clojure.lang ExceptionInfo]))

(defn git? [dep-map]
  (some-> (:git/url dep-map)))

(defn jar? [dep-map]
  (some-> (:mvn/version dep-map)))

(defn as-dep [[k v]]
  (assoc v :name
         (-> (name k)
             (str/split #"/")
             (last))))

(defn resolve-deps [{:keys [deps] :as f}]
  (let [repos (merge mvn/standard-repos (:mvn/repos f))]
    (->> (deps/resolve-deps
          {:deps      deps
           :mvn/repos  repos} nil)
         (map as-dep))))

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

(defn make-classpath []
  (let [gits           (fs/list-dir "lib/git")
        jars           (fs/list-dir "lib/jar")
        as-jar-path    (fn [path]
                         (format "lib/jar/%s.jar" (fs/name path)))
        as-git-path    (fn [path]
                         (format "lib/git/%s/src" (fs/name path)))
        as-res-path    (fn [path]
                         (format "lib/git/%s/resources" (fs/name path)))]
    (->> (concat
         (map as-jar-path jars)
         (map as-git-path gits)
         (map as-res-path gits))
        (str/join ":"))))

(ns pantheon.tools.deps.pack
  (:require
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.java.io :as io]
   [me.raynes.fs :as fs]
   [pantheon.tools.util :as u]))

(defn find-procurer [dep]
  (cond
    (some-> (:git/url dep)) :git
    (some-> (:mvn/version dep)) :jar))

(defn root [type]
  (format "lib/%s" (name type)))

(defn make-path [type path]
  (condp = type
    :git (format "%s/%s" (root type) path)
    :jar (format "%s/%s.jar" (root type) (fs/name path))))

(defmulti copy (fn [dep] (find-procurer dep)))

(defmethod copy :git [dep]
  (->> (make-path :git (:name dep))
       (fs/copy-dir (:deps/root dep))))

(defmethod copy :jar [{:keys [paths] :as dep}]
  (fs/mkdirs (root :jar))
  (doseq [path paths]
    (fs/copy path (make-path :jar path))))

(defn copy-deps [deps]
  (doseq [dep deps]
    (copy dep)))

(defmulti make-classpath (fn [type path _] type))

(defmethod make-classpath :git [type path resource-paths]
  (->> (conj resource-paths "src")
       (distinct)
       (map #(format "%s/%s/%s" (root type) (fs/name path) %))))

(defmethod make-classpath :jar [_ path _]
  (format "%s/%s.jar" (root :jar) (fs/name path)))

(defn find-all-paths [type resource-paths]
  (->> (fs/list-dir (root type))
       (map #(make-classpath type % resource-paths))))

(defn make-all-classpath [resource-paths]
  (->> (concat (find-all-paths :git resource-paths)
               (find-all-paths :jar resource-paths))
       (flatten)
       (str/join ":")))

(ns pantheon.tools.deps.classpath
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

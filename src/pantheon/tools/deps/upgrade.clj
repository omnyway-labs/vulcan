(ns pantheon.tools.deps.upgrade
  (:refer-clojure :exclude [flatten resolve load])
  (:require
   [clojure.string :as str]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [clojure.tools.gitlibs :as gl]
   [clojure.tools.gitlibs.impl :as impl]
   [pantheon.tools.util :as u])
  (:import
   [org.eclipse.jgit.util RefMap]
   [org.eclipse.jgit.revwalk RevWalk]))

(defn make-git-db [url]
  (-> url
      impl/ensure-git-dir
      impl/git-repo))

(defn tag-info [^RevWalk walk ^RefMap tag]
  (let [commit (->> (.. tag getValue getObjectId)
                    (.parseCommit walk))]
    {:time    (.getCommitTime commit)
     :tag     (.getKey tag)
     :sha     (.getName commit)}))

(defn sort-tags [^RevWalk walk tags]
  (->> (map #(tag-info walk %) tags)
       (sort-by :time)))

(defn resolve-master [url]
  {:git/url url
   :sha     (gl/resolve url "master")
   :tag     "master"})

(defn resolve-deps [deps repos]
  (letfn [(as-dep [[k dep]]
            (merge dep
                   {:name (-> (str/split (name k) #"/")
                              (last))}))]
    (->> (deps/resolve-deps
          {:deps      deps
           :mvn/repos  repos} nil)
         (map as-dep))))

(defn find-latest-tag
  "Find the latest tag for given git repo"
  [url]
  (when url
    (let [db   (make-git-db url)
          walk (RevWalk. db)]
      (->> (.getTags db)
           (sort-tags walk)
           (last)))))

(defn make-latest-dep
  "Given a dependency map returns the repository
   with the latest tag and corresponding SHA"
  [dep]
  (let [url (:git/url dep)
        {:keys [time] :as latest} (find-latest-tag url)]
    (merge (dissoc dep :local/root)
           latest
           (when time
             {:time (u/secs->timestamp time)}))))

(defn make-dep [dep]
  (-> dep
      (select-keys [:mvn/version :git/url :local/root
                    :sha :exclusions :dependents :time :tag])
      (update-in [:dependents] (partial (comp vec distinct)))
      (u/remove-nil-entries)))

(defn flatten-all
  "Recursively finds the depedencies and flattens them.
   Latest version in a conflict, wins"
  [deps repos]
  (->> (deps/resolve-deps {:deps      deps
                           :mvn/repos repos} nil)
       (reduce-kv #(assoc %1 %2 (make-dep %3)) {})))

(defn upgrade-to-latest [deps]
  (reduce-kv #(assoc %1 %2 (make-latest-dep %3)) {} deps))

(defn flatten-and-upgrade [all-deps selected-deps repos]
  (let [flat-deps (flatten-all all-deps repos)]
    (->> (upgrade-to-latest selected-deps)
         (merge flat-deps))))

(defn diff-dep
  "Takes a diff of two dep maps
  (diff-dep {:a {:tag 1.0 :time x}}
            {:a {:tag 2.0 :time y}})
  => {:a {1.0 x 2.0 y}
  returns {} if the tags are the same"
  [old new]
  (letfn [(find [m k at]
            (get-in m [k at]))
          (same? [k]
            (= (find old k :tag)
               (find new k :tag)))
          (as-diff [k]
            (when-not (same? k)
              {k {(find old k :tag) (find old k :time)
                  (find new k :tag) (find new k :time)}}))]
    (->> (keys old)
         (map as-diff)
         (into {}))))

(defn flatten [deps repos]
  (->> (flatten-all deps repos)
       (into (sorted-map))))

(defn upgrade [flatten? repos all-deps selected-deps]
  (if flatten?
    (flatten-and-upgrade all-deps selected-deps repos)
    (merge all-deps (upgrade-to-latest selected-deps))))

(defn diff [all-deps selected-deps]
  (->> (upgrade-to-latest selected-deps)
       (diff-dep selected-deps)))

(defn load [resolved-deps paths]
  (-> (deps/make-classpath resolved-deps paths nil)
      (str/split #":")))

(defn pull [deps repos]
  (-> (deps/resolve-deps
       {:deps      deps
        :mvn/repos  repos} nil)))

(defn pull-all [repos deps selected-deps]
  (as-> selected-deps d
    (upgrade false repos deps d)
    (pull d repos)))

(defn pull-latest [deps repos]
  (doseq [[name dep] deps]
    (let [latest (make-latest-dep dep)]
      (prn latest)
      (pull {name latest} repos))))

(defn pull-tag [name dep version repos]
  (let [dep (merge dep {:tag version})]
    (pull {name dep} repos)))

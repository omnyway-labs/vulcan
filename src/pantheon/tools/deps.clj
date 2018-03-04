(ns pantheon.tools.deps
  (:require
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.java.io :as io]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.reader.edn :as edn]
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.tools.gitlibs :as gl]
   [clojure.tools.gitlibs.impl :as impl]
   [pantheon.tools.util :as u])
  (:import
   [java.io PushbackReader]
   [org.eclipse.jgit.util RefMap]
   [org.eclipse.jgit.revwalk RevWalk]))

(defn read-deps-file []
  (with-open [rdr (-> "deps.edn" io/reader (PushbackReader.))]
    (edn/read rdr)))

(defn write-deps-file [data]
  (with-open [w (io/writer "deps.edn")]
    (binding [*out* w
              *print-dup* true]
      (pprint/pprint data))))

(defn find-pantheon-deps [deps]
  (->> deps
       (filter #(str/starts-with? (key %) "omnypay"))
       (into {})))

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

(defn find-latest-tag
  "Find the latest tag for given git repo"
  [url]
  (when url
    (let [db   (make-git-db url)
          walk (RevWalk. db)]
      (->> (.getTags db)
           (sort-tags walk)
           (last)))))

(defn make-pantheon-dep
  "Given a dependency map returns the pantheon repository
   with the latest tag and corresponding SHA"
  [dep]
  (let [url (:git/url dep)
        {:keys [time] :as latest} (find-latest-tag url)]
    (merge dep latest
           (when time
             {:time (u/secs->timestamp time)}))))

(defn make-dep [dep]
  (-> dep
      (select-keys [:mvn/version :git/url :local/root
                    :sha :exclusions :dependents :time :tag])
      (update-in [:dependents] (partial (comp vec distinct)))
      (u/remove-nil-entries)))

(defn flatten-all-deps
  "Recursively finds the depedencies and flattens them.
   Latest version in a conflict, wins"
  [deps]
  (->> (deps/resolve-deps {:deps deps} nil)
       (reduce-kv #(assoc %1 %2 (make-dep %3)) {})))

(defn find-latest-pantheon-deps
  [deps]
  (->> (find-pantheon-deps deps)
       (reduce-kv #(assoc %1 %2 (make-pantheon-dep %3)) {})))

(defn flatten-latest-pantheon-deps [deps]
  (let [flat-deps (flatten-all-deps deps)]
    (->> (find-latest-pantheon-deps flat-deps)
         (merge flat-deps))))

(defn diff [old new]
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

(defn latest-deps-cmd []
  (-> (read-deps-file)
      :deps
      (find-latest-pantheon-deps)))

(defn flatten-cmd []
  (->> (read-deps-file)
       :deps
       (flatten-all-deps)
       (into (sorted-map))))

(defn upgrade-cmd []
  (let [{:keys [deps] :as orig} (read-deps-file)]
    (->> (flatten-latest-pantheon-deps deps)
         (into (sorted-map))
         (assoc orig :deps)
         (into (sorted-map))
         (write-deps-file))))

(defn diff-cmd []
  (let [{:keys [deps]} (read-deps-file)
        orig (find-pantheon-deps deps)
        latest (find-latest-pantheon-deps deps)]
    (diff orig latest)))

(def cli-options
  [["-l" "--latest"]
   ["-f" "--flatten"]
   ["-u" "--upgrade"]
   ["-d" "--diff"]
   ["-h" "--help"]])

(defn prn-edn [edn]
  (binding [*print-dup* true]
    (pprint/pprint edn)))

(defn -main [& args]
  (let [{:keys [options]} (parse-opts args cli-options)
        {:keys [latest upgrade
                flatten diff]} options]
    (when resolve
      (prn-edn (latest-deps-cmd)))
    (when flatten
      (prn-edn (flatten-cmd)))
    (when upgrade
      (upgrade-cmd)
      (println "Wrote deps.edn"))
    (when diff
      (prn-edn (diff-cmd)))))

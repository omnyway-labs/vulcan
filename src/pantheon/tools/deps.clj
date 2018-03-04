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

(defn flatten-deps
  "Recursively finds the depedencies and flattens them.
   Latest version in a conflict, wins"
  [deps]
  (->> (deps/resolve-deps {:deps deps} nil)
       (reduce-kv #(assoc %1 %2 (make-dep %3)) {})))

(defn resolve-pantheon-deps
  [deps]
  (->> (find-pantheon-deps deps)
       (reduce-kv #(assoc %1 %2 (make-pantheon-dep %3)) {})))

(defn flatten-and-resolve [deps]
  (let [flat-deps (flatten-deps deps)]
    (->> (resolve-pantheon-deps flat-deps)
         (merge flat-deps))))

(defn resolve-cmd []
  (-> (read-deps-file)
      :deps
      (resolve-pantheon-deps)))

(defn flatten-cmd []
  (->> (read-deps-file)
       :deps
       (flatten-deps)
       (into (sorted-map))))

(defn upgrade-cmd []
  (let [{:keys [deps] :as orig} (read-deps-file)]
    (->> (flatten-and-resolve deps)
         (into (sorted-map))
         (assoc orig :deps)
         (into (sorted-map))
         (write-deps-file))))

(def cli-options
  [["-r" "--resolve"]
   ["-f" "--flatten"]
   ["-u" "--upgrade"]
   ["-d" "--diff"]
   ["-d" "--diff"]
   ["-h" "--help"]])

(defn prn-edn [edn]
  (binding [*print-dup* true]
    (pprint/pprint edn)))

(defn -main [& args]
  (let [{:keys [options]} (parse-opts args cli-options)
        {:keys [resolve upgrade flatten]} options]
    (when resolve
      (prn-edn (resolve-cmd)))
    (when flatten
      (prn-edn (flatten-cmd)))
    (when upgrade
      (upgrade-cmd)
      (println "Wrote deps.edn"))))

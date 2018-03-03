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
   [org.eclipse.jgit.revwalk
    RevWalk RevTag]))

(defn omethods [obj]
  (map #(.getName %) (-> obj class .getMethods)))

(defn read-deps-file []
  (with-open [rdr (-> "deps.edn" io/reader (PushbackReader.))]
    (edn/read rdr)))

(defn write-deps-file [data]
  (with-open [w (io/writer "deps.edn")]
    (binding [*out* w
              *print-dup* true]
      (pprint/pprint data))))

(defn find-pantheon-repos [deps]
  (->> (keys deps)
       (filter #(str/starts-with? % "omnypay"))
       (map keyword)))

(defn make-git-url [repo]
  (format "git@github.com:omnypay/%s.git" (name repo)))

(defn tag->sha
  "Given a repo and tag, return the sha"
  [repo tag]
  (gl/resolve (make-git-url repo) tag))

(defn sha->tag
  "Given a repo and sha, return the immediate tag"
  [repo tag]
  (gl/descendant (make-git-url repo) [tag]))

(defn latest-tag
  "Get the latest tag for given repo"
  [repo]
  (let [url (make-git-url repo)
        git-dir (impl/ensure-git-dir url)
        walk (RevWalk. (impl/git-repo git-dir))]
    ;; FIXME:
    "master"))

(defn make-dep [repo]
  (let [tag (latest-tag  repo)]
    {(symbol (str  "omnypay/" (name repo)))
     {:git/url (make-git-url repo)
      :tag     tag
      :sha     (tag->sha repo tag)}}))

(defn as-dep [dep-map]
  (-> dep-map
      (select-keys [:mvn/version :git/url :local/root
                    :sha :exclusions :dependents])
      (update-in [:dependents] (partial apply sorted-set))
      (u/remove-nil-entries)))

(defn flatten-deps [deps]
  (->> (deps/resolve-deps {:deps deps} nil)
       (reduce-kv #(assoc %1 %2 (as-dep %3)) {})))

(defn resolve-pantheon-deps [deps]
  (->> (find-pantheon-repos deps)
       (map make-dep)
       (into {})))

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
   ["-u" "--upgrade"]
   ["-d" "--diff"]
   ["-f" "--flatten"]
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

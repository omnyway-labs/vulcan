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
   [org.eclipse.jgit.revwalk RevWalk RevTag]))

(defn omethods [obj]
  (map #(.getName %) (-> obj class .getMethods)))

(defn read-deps-file []
  (with-open [rdr (-> "deps.edn" io/reader (PushbackReader.))]
    (edn/read rdr)))

(defn write-deps-file [data]
  (with-open [w (io/writer "deps.edn")]
    (binding [*out* w *print-dup* true]
      (pprint/pprint data))))

(defn merge-deps [deps]
  (let [orig (read-deps-file)]
    (merge orig
           {:deps (-> (:deps orig)
                      (merge deps))})))

(defn persist! [deps]
  (->> (merge-deps deps)
       (into (sorted-map))
       (write-deps-file)))

(defn find-pantheon-repos [{:keys [deps]}]
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
      (update-in [:dependents] (comp distinct sort))
      (u/remove-nil-entries)))

(defn flatten-deps [deps]
  (->> (deps/resolve-deps deps nil)
       (reduce-kv #(assoc %1 %2 (as-dep %3)) {})))

(defn resolve-latest-tags [repos]
  (->> (map make-dep repos)
       (into {})))

(defn flatten-and-resolve [deps]
  (let [repos (find-pantheon-repos deps)]
    (merge (flatten-deps deps)
           (resolve-latest-tags repos))))

(defn show-cmd []
  (-> (read-deps-file)
      (find-pantheon-repos)
      (resolve-latest-tags)))

(defn flatten-cmd []
  (->> (read-deps-file)
       (flatten-and-resolve)
       (into (sorted-map))))

(defn upgrade-cmd []
  (-> (read-deps-file)
      (flatten-and-resolve)
      (persist!)))

(def cli-options
  [["-s" "--show"]
   ["-u" "--upgrade"]
   ["-d" "--diff"]
   ["-f" "--flatten"]
   ["-r" "--resolve"]
   ["-a" "--all-master"]
   ["-h" "--help"]])

(defn prn-edn [edn]
  (binding [*print-dup* true]
    (pprint/pprint edn)))

(defn -main [& args]
  (let [{:keys [options]} (parse-opts args cli-options)
        {:keys [show upgrade flatten]} options]
    (when show
      (prn-edn (show-cmd)))
    (when flatten
      (prn-edn (flatten-cmd)))
    (when upgrade
      (upgrade-cmd))))

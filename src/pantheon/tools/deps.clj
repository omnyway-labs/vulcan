(ns pantheon.tools.deps
  (:refer-clojure :exclude [flatten resolve])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [pantheon.tools.util :as u]
   [pantheon.tools.commands :refer [defcommand] :as c]
   [pantheon.tools.deps.upgrade :as up]
   [pantheon.tools.deps.link :as link]
   [pantheon.tools.deps.culprit :as culprit]
   [pantheon.tools.deps.pack :as pack])
  (:import
   [java.io PushbackReader]))

(defn read-deps-file
  ([] (read-deps-file "deps.edn"))
  ([deps-file]
   (with-open [rdr (-> deps-file
                       io/reader
                       (PushbackReader.))]
     (edn/read rdr))))

(defn build-repos [repos]
  (merge mvn/standard-repos repos))

(def global-deps-file
  (->> (System/getenv "HOME")
       (format "%s/.clojure/deps.edn")))

(defn find-pantheon-deps [deps]
  (->> deps
       (filter #(str/starts-with? (key %) "omnypay"))
       (into {})))

(defn write-deps-file
  ([data] (write-deps-file data "deps.edn"))
  ([data f]
   (with-open [w (io/writer f)]
     (u/prn-edn data w))))

(defn find-pantheon-deps [deps]
  (->> deps
       (filter #(str/starts-with? (key %) "omnypay"))
       (into {})))

(defn find-local-projects [project deps]
  (let [project (when project
                  (symbol (str "omnypay/" project)))
        local-deps (if project
                     (select-keys deps [project])
                     deps)]
    (find-pantheon-deps local-deps)))

(defn ensure-sorted [orig new]
  (->> (into (sorted-map) new)
       (assoc orig :deps)
       (into (sorted-map))))

(defn do-flatten []
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (-> (up/flatten deps repos)
        (into (sorted-map)))))

(defn do-upgrade [flatten?]
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (->> (find-pantheon-deps deps)
         (up/upgrade flatten? repos deps)
         (ensure-sorted orig))))

(defn do-diff []
  (let [{:keys [deps] :as orig} (read-deps-file)]
    (->> (find-pantheon-deps deps)
         (up/diff deps))))

(defn do-link [project]
  (let [{:keys [deps] :as orig} (read-deps-file)]
    (->> (find-local-projects project deps)
         (link/link)
         u/spy
         (merge deps)
         (ensure-sorted orig))))

(defn do-unlink [project]
  (let [{:keys [deps] :as orig} (read-deps-file)]
    (->> (find-local-projects project deps)
         (link/unlink)
         u/spy
         (merge deps)
         (ensure-sorted orig))))

(defn do-self-update []
  (let [repo "omnypay/pantheon-dev-tools"
        url  (format "git@github.com:%s.git" repo)
        dep  (up/resolve-master url)]
    (u/prn-edn dep)
    (-> (read-deps-file global-deps-file)
        (u/rmerge
         {:aliases
          {:deps
           {:extra-deps {(symbol repo) dep}
            :main-opts  ["-m" "pantheon.tools.deps"]}}}))))

(defn find-culprits []
  (->> (read-deps-file)
       :deps
       (culprit/find-aot-jars)))

(defn do-make-classpath []
  (-> (read-deps-file)
       :paths
       (pack/make-all-classpath)))

(defn do-pack []
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (-> (up/resolve-deps deps repos)
        (pack/copy-deps))))

(defcommand
  ^{:alias "flatten"
    :doc   "Flatten out all dependencies recursively"}
  flatten [opts]
  (u/prn-edn (do-flatten)))

(defcommand
  ^{:alias "upgrade"
    :opts  [["-f" "--flatten"]
            ["-d" "--dry-run"]]
    :doc   "Upgrade Pantheon deps to latest tags"}
  upgrade [{:keys [options]}]
  (let [{:keys [flatten dry-run]} options]
    (let [deps (do-upgrade flatten)]
      (if dry-run
        (u/prn-edn deps)
        (do
          (write-deps-file deps)
          (println "Wrote deps.edn"))))))

(defcommand
  ^{:alias "diff"
    :doc   "Show diff of current and upstream tags for Pantheon repos"}
  diff [opts]
  (u/prn-edn (do-diff)))

(defcommand
  ^{:alias "pack"
    :doc   "Packs Git and Jar dependencies"}
  pack [opts]
  (do-pack)
  (spit ".classpath" (do-make-classpath))
  (println "Copied deps to lib and wrote .classpath"))

(defcommand
  ^{:alias "classpath"
    :doc   "Print the Pack classpath"}
  classpath [opts]
  (println (do-make-classpath)))

(defcommand
  ^{:alias "culprit"
    :doc   "List dependencies which are aot'd or have duplicate namespaces"}
  culprit [opts]
  (u/prn-edn (find-culprits)))

(defcommand
  ^{:alias "self-update"
    :doc   "Update pantheon-dev-tools to latest master SHA"}
  self-update [opts]
  (write-deps-file (do-self-update) global-deps-file))

(defcommand
  ^{:alias "link"
    :opts  [["-p" "--project PROEJCT" "Pantheon Dependency"]]
    :doc   "link local git repos"}
  link [{:keys [options]}]
  (let [{:keys [project]} options]
    (write-deps-file (do-link project))))

(defcommand
  ^{:alias "unlink"
    :opts  [["-p" "--project PROEJCT" "Pantheon Dependency"]]
    :doc   "unlink local git repos"}
  unlink [{:keys [options]}]
  (let [{:keys [project]} options]
    (write-deps-file (do-unlink project))))

(defn -main [& args]
  (c/process args))

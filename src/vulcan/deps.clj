(ns vulcan.deps
  (:refer-clojure :exclude [flatten resolve])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [vulcan.util :as u]
   [vulcan.commands :refer [defcommand] :as c]
   [vulcan.deps.upgrade :as up]
   [vulcan.deps.classpath :as cp]
   [vulcan.deps.link :as link]
   [vulcan.deps.culprit :as culprit]
   [vulcan.deps.pack :as pack]
   [vulcan.deps.uberjar :as uberjar])
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

(defn find-org-deps [prefix deps]
  (->> deps
       (filter #(str/starts-with? (key %) prefix))
       (into {})))

(defn write-deps-file
  ([data] (write-deps-file data "deps.edn"))
  ([data f]
   (with-open [w (io/writer f)]
     (u/prn-edn data w))))

(defn find-local-projects [prefix project deps]
  (let [project (when project
                  (symbol (str prefix "/" project)))
        local-deps (if project
                     (select-keys deps [project])
                     deps)]
    (find-org-deps prefix local-deps)))

(defn ensure-sorted [orig new]
  (->> (into (sorted-map) new)
       (assoc orig :deps)
       (into (sorted-map))))

(defn do-flatten []
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (-> (up/flatten deps repos)
        (into (sorted-map)))))

(defn do-upgrade [prefix flatten?]
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (->> (find-org-deps prefix deps)
         (up/upgrade flatten? repos deps)
         (ensure-sorted orig))))

(defn do-diff [prefix]
  (let [{:keys [deps] :as orig} (read-deps-file)]
    (->> (find-org-deps prefix deps)
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
  (let [repo "omnyway-labs/vulcan"
        url  (format "git@github.com:%s.git" repo)
        dep  (up/resolve-master url)]
    (u/prn-edn dep)
    (-> (read-deps-file global-deps-file)
        (u/rmerge
         {:aliases
          {:deps
           {:extra-deps {(symbol repo) dep}
            :main-opts  ["-m" "vulcan.deps"]}
           :test
           {:extra-deps {(symbol repo) dep}
            :main-opts  ["-m" "vulcan.test"]}}}))))

(defn find-culprits []
  (let [deps (:deps (read-deps-file))]
    (culprit/find-aot-jars deps)
    (culprit/find-overlapping-namespaces deps)))

(defn do-make-classpath []
  (-> (read-deps-file)
       :paths
       (pack/make-all-classpath)))

(defn do-pack []
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (-> (up/resolve-deps deps repos)
        (pack/copy-deps))))

(defn do-uberjar []
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (-> (up/resolve-deps deps repos)
        (uberjar/create))))

(defn read-deps []
  (let [{:keys [deps paths]
         :as   orig} (read-deps-file)
        repos      (build-repos (:mvn/repos orig))]
    {:deps  deps
     :paths (distinct (conj paths "src"))
     :repos repos
     :orig  orig}))

(defn pull
  "Pulls given libs. Does not update deps.edn
  (pull)
     pulls all deps as defined in deps.edn
  (pull {org.clojure/clojure {:mvn/version \"1.9.0\"}})
     pulls given map of dependencies.
  (pull project version)
    Where version is :latest or tag
    e.g (pull :my-project :latest)
     pulls latest sha of my-project
    (pull :my-project 0.1.30)"
  ([prefix]
   (let [{:keys [repos deps]} (read-deps)]
     (->>  (find-org-deps prefix deps)
           (up/pull-all repos deps))))
  ([prefix deps]
   (when (map? deps)
     (->> (:repos (read-deps))
          (up/pull deps))))
  ([prefix lib version]
   (let [{:keys [repos deps]} (read-deps)
         name  (symbol (str prefix "/" (name lib)))
         dep   (get deps name)]
     (if (= :latest version)
       (up/pull-latest {name dep} repos)
       (up/pull-tag name dep version repos)))))

(defn make-classpath [resolved-deps]
  (->> (read-deps)
       :paths
       (cp/make-all-classpath resolved-deps)))

(defn current-classpath []
  (cp/current-classpath))

(defn import!
  "Import specified libs into current project and Repl"
  [& args]
  (-> (apply pull args)
      (make-classpath)
      (cp/add-all!)))

(defcommand
  ^{:alias "flatten"
    :doc   "Flatten out all dependencies recursively"}
  flatten [opts]
  (u/prn-edn (do-flatten)))

(defcommand
  ^{:alias "upgrade"
    :opts  [["-f" "--flatten"]
            ["-d" "--dry-run"]
            ["-p" "--prefix PREFIX" "Prefix - typically Github Org"]]
    :doc   "Upgrade deps to latest tags for given prefix"}
  upgrade [{:keys [options]}]
  (let [{:keys [prefix flatten dry-run]} options]
    (if prefix
      (let [deps (do-upgrade prefix flatten)]
        (if dry-run
          (u/prn-edn deps)
          (do
            (write-deps-file deps)
            (println "Wrote deps.edn"))))
      (println "No Prefix provided"))))

(defcommand
  ^{:alias "diff"
    :opts [["-p" "--prefix PREFIX" "Prefix - typically Github Org"]]
    :doc   "Show diff of current and upstream tags for repos in given org (prefix)"}
  diff [{:keys [options]}]
  (let [{:keys [prefix]} options]
    (u/prn-edn (do-diff prefix))))

(defcommand
  ^{:alias "pack"
    :doc   "Packs Git and Jar dependencies"}
  pack [opts]
  (do-pack)
  (spit ".classpath" (do-make-classpath))
  (println "Copied deps to lib and wrote .classpath"))

(defcommand
  ^{:alias "uberjar"
    :doc   "Pack deps into a lambda zip"}
  uberjar [opts]
  (do-uberjar)
  (println "created test.uberjar"))

(defcommand
  ^{:alias "classpath"
    :doc   "Print the Pack classpath"}
  classpath [opts]
  (println (do-make-classpath)))

(defcommand
  ^{:alias "culprit"
    :doc   "List dependencies which are aot'd or have duplicate namespaces"}
  culprit [opts]
  (find-culprits))

(defcommand
  ^{:alias "self-update"
    :doc   "Update vulcan to latest master SHA"}
  self-update [opts]
  (write-deps-file (do-self-update) global-deps-file))

(defcommand
  ^{:alias "link"
    :opts  [["-p" "--project PROEJCT" "Project name"]]
    :doc   "link local git repos"}
  link [{:keys [options]}]
  (let [{:keys [project]} options]
    (write-deps-file (do-link project))))

(defcommand
  ^{:alias "unlink"
    :opts  [["-p" "--project PROEJCT" "Project name"]]
    :doc   "unlink local git repos"}
  unlink [{:keys [options]}]
  (let [{:keys [project]} options]
    (write-deps-file (do-unlink project))))

(defn -main [& args]
  (c/process args))

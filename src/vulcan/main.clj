(ns vulcan.main
  (:refer-clojure :exclude [flatten resolve])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [vulcan.util :as u]
   [vulcan.commands :refer [defcommand] :as c]
   [vulcan.deps :as deps]
   [vulcan.test :as test]
   [vulcan.semver :as semver])
  (:import
   [java.io PushbackReader]))

(defcommand
  ^{:alias "flatten"
    :doc   "Flatten out all dependencies recursively"}
  flatten-command [opts]
  (u/prn-edn (deps/flatten)))

(defcommand
  ^{:alias "upgrade"
    :opts  [["-f" "--flatten"]
            ["-d" "--dry-run"]
            ["-p" "--prefix PREFIX" "Prefix - typically Github Org"]]
    :doc   "Upgrade deps to latest tags for given prefix"}
  upgrade-command [{:keys [options]}]
  (let [{:keys [prefix flatten dry-run]} options]
    (if prefix
      (let [deps (deps/upgrade prefix flatten)]
        (if dry-run
          (u/prn-edn deps)
          (do
            (deps/write-deps-file deps)
            (println "Wrote deps.edn"))))
      (println "No Prefix provided"))))

(defcommand
  ^{:alias "diff"
    :opts [["-p" "--prefix PREFIX" "Prefix - typically Github Org"]]
    :doc   "Show diff of current and upstream tags for repos in given org (prefix)"}
  diff-command [{:keys [options]}]
  (let [{:keys [prefix]} options]
    (if prefix
      (u/prn-edn (deps/diff prefix))
      (println "No Prefix provided"))))

(defcommand
  ^{:alias "pack"
    :doc   "Packs Git and Jar dependencies"}
  pack-command [opts]
  (deps/pack)
  (spit ".classpath" (deps/make-classpath))
  (println "Copied deps to lib and wrote .classpath"))

(defcommand
  ^{:alias "classpath"
    :doc   "Print the Pack classpath"}
  classpath-command [opts]
  (println (deps/make-classpath)))

(defcommand
  ^{:alias "conflicts"
    :opts [["-p" "--prefix PREFIX" "Prefix - typically Github Org"]]
    :doc   "Find overlapping or conflicting namespaces for given org"}
  conflicts-command [{:keys [options]}]
  (let [{:keys [prefix]} options]
    (when prefix
      (deps/find-culprits prefix))))

(defcommand
  ^{:alias "self-update"
    :doc   "Update vulcan to latest master SHA"}
  self-update-command [opts]
  (deps/write-deps-file (deps/self-update) deps/global-deps-file))

(defcommand
  ^{:alias "link"
    :opts  [["-p" "--project PROEJCT" "Project name"]]
    :doc   "link local git repos"}
  link-command [{:keys [options]}]
  (let [{:keys [project]} options]
    (deps/write-deps-file (deps/link project))))

(defcommand
  ^{:alias "unlink"
    :opts  [["-p" "--project PROEJCT" "Project name"]]
    :doc   "unlink local git repos"}
  unlink-command [{:keys [options]}]
  (let [{:keys [project]} options]
    (deps/write-deps-file (deps/unlink project))))

(defcommand
  ^{:alias "test"
    :opts [["-s" "--selector SELECTOR" "Test selector to be included"]]
    :doc   "Run test for given selector"}
  test-command [{:keys [options]}]
  (let [{:keys [selector]} options
        selector (keyword selector)]
    (if (= selector :unit)
      (test/run-test nil)
      (test/run-test selector))))

(defcommand
  ^{:alias "next-tag"
    :opts [["-d" "--directory PROJECT-DIRECTORY" "Path to project directory"]]
    :doc "Generate the next semantic version tag"}
  next-tag-command [{:keys [options]}]
  (let [{:keys [directory]} options]
    (print (semver/next-tag (or (not-empty directory) ".")))))

(defn -main [& args]
  (c/process args))

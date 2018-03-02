(ns pantheon.tools.deps
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]
   [clojure.tools.gitlibs :as gl]
   [clojure.tools.gitlibs.impl :as impl])
  (:import
   [java.io PushbackReader]
   [org.eclipse.jgit.revwalk RevWalk RevTag]))

(defn omethods [obj]
  (map #(.getName %) (-> obj class .getMethods)))

(defn read-deps-file []
  (with-open [rdr (-> "deps.edn" io/reader (PushbackReader.))]
    (edn/read rdr)))

(defn list-omnypay-repos []
  (->> (:deps (read-deps-file))
       keys
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
/  "Get the latest tag for given repo"
  [repo]
  (let [url (make-git-url repo)
        git-dir (impl/ensure-git-dir url)
        walk (RevWalk. (impl/git-repo git-dir))]
    walk))

(defn latest-tags
  "List latest tags for all git procurers defined in deps.edn"
  []
  )

(defn update-latest-tags
  "update deps.edn with latest tags and resolve-tags"
  []
  )

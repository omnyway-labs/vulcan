(ns vulcan.semver.git.cgit
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [vulcan.semver.git.core :refer :all]))

(defn git-cmd [path cmd & args]
  (let [f #(let [{:keys [exit out err]} (apply sh/sh "git" cmd args)]
             (if (= 0 exit)
               (str/trim-newline out)
               (throw
                (ex-info "Git command execution error"
                         {:status :error :err err}))))]
    (if path
      (sh/with-sh-dir path (f))
      (f))))

(defn as-repo [path] path)

(defn empty-tree-hash []
  (git-cmd nil "hash-object" "-t" "tree" "/dev/null"))

(defn revlist [path earlier later]
  (-> (git-cmd path
               "rev-list"
               (str earlier ".." later)
               "--count")
      Integer/parseInt))

(defn distance
  ([path commit]
   (distance path commit "HEAD"))
  ([path earlier later]
   (revlist path earlier later)))

(defn tags [path raw?]
  (-> (git-cmd path "tag" "--sort=-creatordate")
      str/split-lines))

(deftype CGitClient []
  IGit
  (-tags [_ path raw?]
    (tags path raw?))
  (-empty-tree-hash [_]
    (empty-tree-hash))
  (-distance [_ path commit]
    (distance path commit))
  (-as-repo [_ path]
    (as-repo path)))

(defn make-client []
  (CGitClient.))

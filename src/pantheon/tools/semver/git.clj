(ns pantheon.tools.semver.git
  (:require
   [clojure.java.io :as io])
  (:import
   (org.eclipse.jgit.lib ObjectId
                         ObjectInserter
                         ObjectInserter$Formatter
                         Constants
                         Repository)
   (org.eclipse.jgit.storage.file FileRepositoryBuilder)
   (org.eclipse.jgit.api Git)
   (org.eclipse.jgit.api.errors GitAPIException)
   (org.eclipse.jgit.revwalk RevCommit)))

(defn as-repo [path]
  (let [path (if (and (string? path)
                      (not (re-find #"\.git$" path)))
               (str path "/.git")
               path)]
    (if (instance? Repository path)
      path
      (-> (FileRepositoryBuilder.)
          (.setGitDir (io/as-file path))
          (.readEnvironment)
          (.findGitDir)
          (.build)))))

(defn hash-object
  ([path]
   (hash-object path Constants/OBJ_BLOB))
  ([path type]
   (let [f (io/file path)]
     (with-open [in (io/input-stream f)]
       (-> (ObjectInserter$Formatter.)
           (.idFor type 0 in)
           (.name))))))

(defn empty-tree-hash []
  (hash-object "/dev/null" Constants/OBJ_TREE))

(defn revlist [path earlier later]
  (let [repo (as-repo path)
        ref (.resolve repo earlier)
        master (.resolve repo "master")]
    (when ref
      (-> (Git. repo)
          (.log)
          (.addRange ref master)
          (.call)
          seq))))  

(defn distance
  ([path earlier]
   (distance path earlier "HEAD"))
  ([path earlier later]
   (max 0 (dec (count (revlist path earlier later))))))

(defn tags [path & [raw?]]
  (let [repo (as-repo path)]
    (->> (Git. repo)
         (.tagList)
         (.call)
         (map
          (fn [ref]
            (let [ref-name (.getName ref)]
              (if raw?
                ref-name
                (subs ref-name 10))))))))

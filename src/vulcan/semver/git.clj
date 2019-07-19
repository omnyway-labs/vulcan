(ns vulcan.semver.git
  (:require
   [vulcan.semver.git.core :refer [-tags
                                   -empty-tree-hash
                                   -distance
                                   -as-repo]]
   [vulcan.semver.git.jgit :as jgit]
   [vulcan.semver.git.cgit :as cgit]))

(def ^:dynamic *git-client* (jgit/make-client))

(def client-constructors
  {:jgit jgit/make-client
   :cgit cgit/make-client})

(defn as-client [client]
  (if (keyword? client)
    (apply (client-constructors client) nil)
    client))

(defmacro with-client [client & body]
  `(binding [*git-client* (as-client ~client)]
     ~@body))

(defn tags
  ([path]
   (tags path false))
  ([path raw?]
   (-tags *git-client* path raw?)))

(defn empty-tree-hash []
  (-empty-tree-hash *git-client*))

(defn distance [path commit]
  (-distance *git-client* path commit))

(defn as-repo [path]
  (-as-repo *git-client* path))

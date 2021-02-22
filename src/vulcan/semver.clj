(ns vulcan.semver
  (:require
   [clojure.string :as str]
   [vulcan.util :as u]
   [vulcan.commands :refer [defcommand] :as c]
   [vulcan.semver.git :as git]))

(defn as-int [x]
  (or (u/ignore-errors (Integer/parseInt x)) 0))

(def semver-regex #"([0-9]+)\.([0-9]+)\.([0-9]+)")

(defn parse-semver [v]
  (let [[_ maj min patch] (first (re-seq semver-regex v))]
    [(as-int maj) (as-int min) (as-int patch)]))

(defn semver-to-int [v]
  (let [[maj min patch] (parse-semver v)]
    (+ patch
       (* 1000 min)
       (* 1000000 maj))))

(defn semver-tags [path]
  (->> (git/tags path)
       (filter
        (fn [tag]
          (re-find semver-regex tag)))
       (sort
        (fn [a b]
          (> (semver-to-int a)
             (semver-to-int b))))))

(defn latest-semver-tag [path]
  (first (semver-tags path)))

(def release-tag-regex #"RELEASE-([0-9]+)\.([0-9]+)")

(defn release-tags [path]
  (->> (git/tags path)
       (filter
        (fn [tag]
          (re-find release-tag-regex tag)))
       (sort #(.compareTo %2 %1))))

(defn latest-release-tag [path]
  (or (first (release-tags path))
      (throw (RuntimeException.
              (format "No release tags matching the pattern `%s` were found"
                      release-tag-regex)))))

(defn latest-release-version [path]
  (if-let [release-tag (latest-release-tag path)]
    (let [[_ rel-maj rel-min] (first
                               (re-seq release-tag-regex release-tag))]
      [(as-int rel-maj) (as-int rel-min)])
    [0 1]))

(defn next-semver-version [path]
  (let [earlier-commit (or (latest-semver-tag path) (git/empty-tree-hash))
        dist (git/distance path earlier-commit)
        delta (if (pos? dist) 1 0)
        [maj min patch] (parse-semver earlier-commit)
        [rel-maj rel-min] (latest-release-version path)
        patch (if (or (< maj rel-maj) (< min rel-min))
                0
                patch)]
    [(max rel-maj maj)
     (max rel-min min)
     (+ patch delta)]))

(defn next-tag
  ([path]
   (next-tag path {}))
  ([path {:keys [use-cgit?]}]
   (git/with-client (if use-cgit? :cgit :jgit)
     (let [repo (git/as-repo path)
           semver (next-semver-version repo)]
       (str/join "." semver)))))

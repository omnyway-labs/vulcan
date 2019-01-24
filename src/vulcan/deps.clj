(ns vulcan.deps
  (:refer-clojure :exclude [flatten resolve])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [vulcan.util :as u]
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

(defn find-project [project deps]
  (let [project-sym (symbol project)]
    (select-keys deps [project-sym])))

(defn ensure-sorted [orig new]
  (->> (into (sorted-map) new)
       (assoc orig :deps)
       (into (sorted-map))))

(defn flatten []
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (-> (up/flatten deps repos)
        (into (sorted-map)))))

(defn upgrade [prefix flatten?]
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (->> (find-org-deps prefix deps)
         (up/upgrade flatten? repos deps)
         (ensure-sorted orig))))

(defn diff [prefix]
  (let [{:keys [deps] :as orig} (read-deps-file)]
    (->> (find-org-deps prefix deps)
         (up/diff deps))))

(defn link [project]
  (let [{:keys [deps] :as orig} (read-deps-file)]
    (->> (find-project project deps)
         (link/link)
         u/spy
         (merge deps)
         (ensure-sorted orig))))

(defn unlink [project]
  (let [{:keys [deps] :as orig} (read-deps-file)]
    (->> (find-project project deps)
         (link/unlink)
         u/spy
         (merge deps)
         (ensure-sorted orig))))

(defn self-update []
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

(defn find-culprits [prefix]
  (->> (read-deps-file)
       :deps
       (culprit/find-overlapping-namespaces prefix)))

(defn pack []
  (let [{:keys [deps] :as orig} (read-deps-file)
        repos (build-repos (:mvn/repos orig))]
    (-> (up/resolve-deps deps repos)
        (pack/copy-deps))))

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

(defn make-classpath
  ([]
   (-> (read-deps-file)
       :paths
       (pack/make-all-classpath)))
  ([resolved-deps]
   (->> (read-deps)
        :paths
        (cp/make-all-classpath resolved-deps))))

(defn current-classpath []
  (cp/current-classpath))

(defn import!
  "Import specified libs into current project and Repl"
  [& args]
  (-> (apply pull args)
      (make-classpath)
      (cp/add-all!)))

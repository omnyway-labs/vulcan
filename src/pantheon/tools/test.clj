(ns pantheon.tools.test
  (:refer-clojure :exclude [ns])
  (:require
   [clojure.tools.namespace.find :as find]
   [clojure.java.io :as io]
   [cognitect.test-runner :as runner]
   [pantheon.tools.util :as u]
   [pantheon.tools.commands :refer [defcommand] :as c]))

(defn fail? [{:keys [fail error]}]
  (or (pos? fail) (pos? error)))

(def hooks (atom nil))

;; copy of test-runner/ns-filter
(defn ns-filter [{:keys [namespace namespace-regex]}]
  (let [regexes (or namespace-regex [#".*\-test$"])]
    (fn [ns]
      (or (and namespace (namespace ns))
          (some #(re-matches % (name ns)) regexes)))))

(defn prn-test-namespaces [options]
  (let [dirs #{"test"}
        nses (->> dirs
                  (map io/file)
                  (mapcat find/find-namespaces-in-dir))
        nses (filter (ns-filter options) nses)]
    (doseq [n nses]
      (prn n))))

(def selectors
  #{:api :scenario :fixme :integration-fixme
    :integration :secure :concurrent :unit})

(defn run-test [selector]
  (try
    (let [opts {:include (when selector #{selector})
                :exclude (disj selectors selector)}
          result (runner/test (u/remove-nil-entries opts))]
      (println result)
      (if (fail? result)
        (System/exit 1)
        (System/exit 0)))
    (finally
      (shutdown-agents))))

(defcommand
  ^{:alias "integration"
    :opts [["-d" "--dry-run"]]
    :doc   "Run Integration tests"}
  integration [{:keys [options]}]
  (run-test :integration))

(defcommand
  ^{:alias "unit"
    :opts [["-d" "--dry-run"]]
    :doc   "Run unit tests"}
  unit [{:keys [options]}]
  (run-test nil))

(defcommand
  ^{:alias "api"
    :opts [["-d" "--dry-run"]]
    :doc   "Run API tests"}
  api [{:keys [options]}]
  (run-test :api))

(defcommand
  ^{:alias "scenario"
    :opts [["-d" "--dry-run"]]
    :doc   "Run Scenario tests"}
  scenario [{:keys [options]}]
  (run-test :scenario))

(defcommand
  ^{:alias "concurrent"
    :opts [["-d" "--dry-run"]]
    :doc   "Run Concurrent tests"}
  concurrent [{:keys [options]}]
  (run-test :concurrent))

(defn -main [& args]
  (c/process args))

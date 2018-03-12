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

(defn run-test [selectors]
  (try
    (let [result (runner/test selectors)]
      (println result)
      (if (fail? result)
        (System/exit 1)
        (System/exit 0)))
    (finally
      (shutdown-agents))))

(def selectors
  #{:api :test :scenario :fixme :integration-fixme
    :integration :secure})

(defn maybe-run-test [{:keys [dry-run]} selector]
  (let [opts {:include #{:test}
              :exclude (disj selectors :test)}]
    (if dry-run
      (prn-test-namespaces opts)
      (run-test
       {:include #{selector}
        :exclude (disj selectors selector)}))))

(defcommand
  ^{:alias "integration"
    :opts [["-d" "--dry-run"]]
    :doc   "Run Integration tests"}
  integration [{:keys [options]}]
  (maybe-run-test options :integration))

(defcommand
  ^{:alias "unit"
    :opts [["-d" "--dry-run"]]
    :doc   "Run unit tests"}
  unit [{:keys [options]}]
  (maybe-run-test options :unit))

(defcommand
  ^{:alias "api"
    :opts [["-d" "--dry-run"]]
    :doc   "Run API tests"}
  api [{:keys [options]}]
  (maybe-run-test options :api))

(defcommand
  ^{:alias "scenario"
    :opts [["-d" "--dry-run"]]
    :doc   "Run Scenario tests"}
  scenario [{:keys [options]}]
  (maybe-run-test options :scenario))

(defcommand
  ^{:alias "ns"
    :opts [["-n" "--namespace NAMESPACE" "Namespace"]]
    :doc   "Run tests in a specific ns"}
  ns [{:keys [options]}]
  (run-test
   {:namespace (symbol namespace)}))

(defn -main [& args]
  (c/process args))

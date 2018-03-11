(ns pantheon.tools.deps-test
  (:require
   [clojure.test :refer :all]
   [pantheon.tools.deps :as deps]))

(deftest basic
  (is (= 2 2)))

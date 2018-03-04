(ns pantheon.tools.orpheus
  (:require
   [clojure.string :as str]
   [pantheon.tools.commands :as c :refer [defcommand]]))

(defn orpheus-request? [form]
  (= (first form) '>>))

(def example-cue-forms
  {:foo
   {:foo.create '((>> :bar.find args)
                  (>> :bar.create args))
    :foo.get    '(prn :get)
    :foo.delete '(>> :baz.delete args)}
   :bar
   {:bar.find   '((println :bar)
                  (>> :baz.get args)
                  (get :a)
                  (>> :baz.get args))
    :bar.create '(>> :baz.update args)}
   :baz
   {:baz.update '(prn :update)
    :baz.get    '(>> :foo.get :a)}})

(def example-graph
  [[:foo.create
    [:bar.find [:baz.get :foo.get]]
    [:bar.create [:baz.update]]]
   [:foo.delete [:baz.delete]]])

(defn generate-cue-forms
  "Given a list of namespaces with orpheus requests,
   generates a mapping of the form {ns {cue form}}.
  `form` is the function body"
  [path]
  :impl)

(defn parse-cue-form
  "Given a cue-form, identify the forms which are orpheus requests.
   return {:foo.create [:bar.find :bar.create]}"
  [{:keys [cue form]}]
  :impl)

(defn build-graph
  "Give cue-forms mapping, builds an orpheus graph
  [[:foo.create [:bar.find [:baz.get :foo.get] :bar.create :baz.update]]
   [:foo.delete :baz.delete]]"
  [cue-forms]
  (->> (map parse-cue-form cue-forms)
       (into {})))

(defn generate-graph
  "Given a root-cue and path, statically generate and orpheus graph"
  [path root-cue]
  (->> (generate-cue-forms path)
       (build-graph)))

(defn format-graph
  ([graph] (apply str (map #(format-graph % "") graph)))
  ([graph prefix]
   (if (vector? graph)
     (->> (map #(format-graph % (str prefix "-")) graph)
          (apply str))
     (str prefix graph "\n"))))

(defcommand
  ^{:alias "graph"
    :doc   "Generate a graph of orpheus cues"}
  graph [opts]
  (println
   (format-graph example-graph)))

(defn -main [& args]
  (c/process args))

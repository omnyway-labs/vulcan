(ns pantheon.tools.swagger
  (:require
   [clojure.string :as str]
   [scjsv.core :as s]
   [pantheon.tools.commands :as c :refer [defcommand]]))

(defn make-validator [swagger-json-schema]
  (-> (slurp swagger-json-schema)
      s/json-validator))

(defn do-validate [swagger-file schema-file]
  (let [swagger-json (slurp swagger-file)
        validator    (make-validator schema-file)]
    (when-let [errs (not-empty (validator swagger-json))]
      (doseq [{:as err :keys [level instance message]} errs]
        (println (str/upper-case level) instance message))
      (System/exit 1))))

(defcommand
  ^{:alias "validate"
   :description "Validate Swagger given a swagger schema"}
  validate [{:keys [options]}]
  (do-validate "swagger.json" "etc/swagger-schema.json"))

(defn -main [& args]
  (c/process args))

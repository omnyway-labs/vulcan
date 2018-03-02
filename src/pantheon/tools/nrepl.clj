(ns pantheon.tools.nrepl
  (:require
   [clojure.tools.nrepl.server :refer [start-server]]
   [cider.nrepl :refer [cider-nrepl-handler]]))

(defn start []
  (let [port (or (some-> (first *command-line-args*)
                       (java.lang.Long/parseLong))
                 9000)]
    (start-server :port port :handler cider-nrepl-handler)
    (println "Started nREPL on port" port)))

(defn -main [& args]
  (start))

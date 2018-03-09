(ns pantheon.tools.commands
  (:require
   [clojure.tools.cli :as cli])
  (:import
   [clojure.lang ExceptionInfo]))

(defonce commands (atom {}))

(defn add-command! [cname handler-fn]
  (swap! commands assoc cname handler-fn))

(defmacro defcommand [cname & rest]
  `(do
     (defn ~cname ~@rest)
     (add-command! (or (:alias (meta '~cname)) (name '~cname)) #'~cname)))

(defn find-handler [command]
  (get @commands command))

(defn print-usage
  ([opts]
   (print-usage opts nil))
  ([{:keys [summary]} header]
   (when header
     (println header))
   (when (not (empty? summary))
     (println "\nAvailable options:")
     (println summary))
   (println "\nAvailable commands:")
   (doseq [command (-> @commands keys sort)]
     (let [{:keys [doc opts]} (meta (find-handler command))]
       (println (format "  %-10s %s" command doc))
       (when opts
         (doseq [opt opts]
           (println
            (format "    %2s %-27s %s"
                    (first opt)
                    (if (< 2 (count opt)) (second opt) "")
                    (last opt)))))))
   (println)))

(defn get-options [command]
  (-> (find-handler command) meta :opts))

(defn execute! [command parsed-opts]
  (let [handler (find-handler command)]
    (if handler
      (handler parsed-opts)
      (throw
       (ex-info (format "Unrecognized command: %s" command)
                {:reason :unrecognized-command
                 :command command
                 :opts parsed-opts})))))

(defn parse-command [arguments]
  (let [command (first arguments)]
    {:command command
     :parsed-opts (cli/parse-opts (rest arguments)
                                  (get-options command)
                                  :in-order true)}))

(defcommand
  ^{:alias "help"
    :doc "Display basic documentation"}
  help-command [opts]
  (print-usage opts "Usage:"))

(defn default-error-handler [ex {:keys [opts]}]
  (println (.getMessage ex))
  (help-command opts))

(defn process [arguments & {:keys [on-help on-error]}]
  (try
    (loop [{:keys [command parsed-opts]} (parse-command arguments)]
      (let [{:keys [options summary arguments]} parsed-opts]
        (if (or (= "help" command) (:help options))
          ((or on-help help-command) parsed-opts)
          (execute! command parsed-opts))
        (when (not (empty? arguments))
          (recur (parse-command arguments)))))
    (catch ExceptionInfo ex
      (if on-error
        (on-error ex)
        (let [{:as ex-map :keys [reason command]} (ex-data ex)]
          (if (= :unrecognized-command reason)
            (default-error-handler ex ex-map)
            (println ex)))))))

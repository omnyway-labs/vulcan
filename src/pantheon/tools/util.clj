(ns pantheon.tools.util
  (:require
   [clojure.pprint :as pprint]
   [clojure.string :as str])
  (:import
   [java.util Calendar Date TimeZone UUID]
   java.text.SimpleDateFormat))

(defmacro ignore-errors
  "Evaluate body and return `nil` if any exception occurs."
  [& body]
  `(try
     ~@body
     (catch Throwable e# nil)))

(defn remove-nil-entries
  "remove keys from the map if their values are nil"
  [m]
  (reduce-kv #(if (and %3 (not (empty? %3)))
                (assoc %1 %2 %3) %1)
             {}
             m))

(def ^:constant iso-8601-format "yyyy-MM-dd'T'HH:mm:ss.SSSZ")

(defn ^SimpleDateFormat simple-date-formatter
  "Create a java.text.SimpleDateFormat with `format-string` and
  optional `tz` timezone."
  ([format-string]
   (simple-date-formatter format-string "UTC"))
  ([format-string tz]
   (doto (SimpleDateFormat. format-string)
     (.setTimeZone (if (string? tz)
                     (TimeZone/getTimeZone tz)
                     tz)))))

(def ^:constant iso-8601-formatter
  (simple-date-formatter "yyyy-MM-dd'T'HH:mm:ss.SSSZ"))

(defn as-formatter [formatter-args]
  (let [[formatter & _] formatter-args]
    (if (instance? SimpleDateFormat formatter)
      formatter
      (apply simple-date-formatter formatter-args))))

(defn parse-timestamp
  "Parse a timestamp string into a java.util.Date, e.g.,

  (parse-timestamp \"2001-01-01T12:34:56.789+0000\")
  => #inst \"2001-01-01T12:34:56.789-00:00\"

  (def formatter (simple-date-formatter \"MM/dd/yyyy HH:mm:ss\" \"America/Los_Angeles\"))
  (parse-timestamp \"01/01/2001 12:34:56\" formatter)
  => #inst \"2001-01-01T20:34:56.000-00:00\"

  (parse-timestamp \"01/01/2001\" \"MM/dd/yyyy\")
  => #inst \"2001-01-01T00:00:00.000-00:00\"

  (parse-timestamp \"01/01/2001 12:34:56\" \"MM/dd/yyyy HH:mm:ss\" \"America/Los_Angeles\")
  => #inst \"2001-01-01T20:34:56.000-00:00\""
  {:arglists '([timestamp-str]
               [timestamp-str formatter]
               [timestamp-str format-str]
               [timestamp-str format-str timezone])}
  ([timestamp-str]
   (parse-timestamp timestamp-str iso-8601-formatter))
  ([timestamp-str & formatter-args]
   (.parse (as-formatter formatter-args) timestamp-str)))

(defn format-timestamp
  "Format a timestamp using a SimpleDateFormat, by defaults this
  function formats as ISO 8601 timestamp string, e.g.,

  (format-timestamp)
  => \"2017-04-12T17:18:37.363+0000\"

  (format-timestamp (System/currentTimeMillis))
  => \"2017-04-12T17:18:37.363+0000\"

  (format-timestamp (System/currentTimeMillis) \"yyyy/MM\")
  => \"2017/04\"
  "
  ([]
   (format-timestamp (System/currentTimeMillis)))
  ([timestamp]
   (format-timestamp timestamp iso-8601-formatter))
  ([timestamp & formatter-args]
   (.format (as-formatter formatter-args) timestamp)))

(defn secs->timestamp [secs]
  (format-timestamp (* secs 1000)))

(defn omethods [obj]
  (map #(.getName %) (-> obj class .getMethods)))

(defn prn-edn [edn]
  (binding [*print-dup* true]
    (pprint/pprint edn)))

(defn rmerge
  "Recursive merge of the provided maps. e.g.,

  (rmerge {:foo {:bar {:baz 1}}} {:foo {:bar {:quux 2}}})
  => {:foo {:bar {:baz 1, :quux 2}}}"
  [& maps]
  (if (every? map? maps)
    (apply merge-with rmerge maps)
    (last maps)))

(defn spy [edn]
  (prn-edn edn)
  edn)

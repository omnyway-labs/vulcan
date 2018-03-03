(ns pantheon.tools.util)

(defmacro ignore-errors
  "Evaluate body and return `nil` if any exception occurs."
  [& body]
  `(try
     ~@body
     (catch Throwable e# nil)))

(defn remove-nil-entries
  "remove keys from the map if their values are nil"
  [m]
  (reduce-kv #(if %3 (assoc %1 %2 %3) %1)
             {}
             m))

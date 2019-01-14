(ns vulcan.deps.link)

(defn resolve-local [dep-name dep]
  (merge {:local/root (str "../" (name dep-name))}
         dep))

(defn link [deps]
  (reduce-kv #(assoc %1 %2 (resolve-local %2 %3)) {} deps))

(defn unlink [deps]
  (reduce-kv #(assoc %1 %2 (dissoc %3 :local/root)) {} deps))

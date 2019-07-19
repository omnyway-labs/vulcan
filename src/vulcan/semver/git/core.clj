(ns vulcan.semver.git.core)

(defprotocol IGit
  (-tags [this path] [this path raw?])
  (-empty-tree-hash [this])
  (-distance [this path commit])
  (-as-repo [this path]))

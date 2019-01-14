(ns vulcan.deps.uberjar
  (:require
   [clojure.string :as str]
   [clojure.pprint :as pprint]
   [clojure.java.io :as io]
   [me.raynes.fs :as fs]
   [pantheon.tools.util :as u]
   [pantheon.tools.deps.pack :as pack])
  (:import [java.io InputStream OutputStream PushbackReader]
           [java.nio.file CopyOption LinkOption OpenOption
                          StandardCopyOption StandardOpenOption
                          FileSystem FileSystems Files
                          FileVisitResult FileVisitor
                          Path]
           [java.nio.file.attribute BasicFileAttributes FileAttribute]
           [java.util.jar JarInputStream JarOutputStream JarEntry]))


(defn write-jar
  [^Path src ^Path target]
  (with-open [os (-> target
                     (Files/newOutputStream (make-array OpenOption 0))
                     JarOutputStream.)]
    (let [walker (reify FileVisitor
                   (visitFile [_ p attrs]
                     (.putNextEntry os (JarEntry. (.toString (.relativize src p))))
                     (Files/copy p os)
                     FileVisitResult/CONTINUE)
                   (preVisitDirectory [_ p attrs]
                     (when (not= src p) ;; don't insert "/" to zip
                       (.putNextEntry os (JarEntry. (str (.relativize src p) "/")))) ;; directories must end in /
                     FileVisitResult/CONTINUE)
                   (postVisitDirectory [_ p ioexc]
                     (if ioexc (throw ioexc) FileVisitResult/CONTINUE))
                   (visitFileFailed [_ p ioexc] (throw ioexc)))]
      (Files/walkFileTree src walker)))
  :ok)

(defn create [deps]
  (pack/copy-deps deps)
  (write-jar "lib" "test.uberjar"))

(ns bbyaga.tasks
  (:require
   [babashka.fs :as fs]
   [babashka.tasks :refer [clojure]]
   [babashka.wait :as wait]))

;; Ref: https://github.com/babashka/babashka/discussions/1122
;; -? how to have clojure load deps from bbyaga/deps.edn but still operate on local project?
;;    - can find directory if loaded from local root
;;    - shared bb fn that loads deps.edn from local root or jar?
;;    - ? how does jarvis do it?
;; -? dogfood: can it run oudated on iteself?
(defn antq
  "Check for outdated dependencies."
  [& _args]
  (let [deps '{:deps {com.github.liquidz/antq {:mvn/version "RELEASE"}
                      org.slf4j/slf4j-nop {:mvn/version "RELEASE"}}}]
    (clojure
     "-Sdeps" (str deps)
     "-M" "-m" "antq.core")))

(defn nrepl
  "Start an nrepl server."
  [& _args]
  (let [nrepl-port (with-open [sock (java.net.ServerSocket. 0)] (.getLocalPort sock))
                        pb         (doto (ProcessBuilder. (into ["bb"
                                                                 "--nrepl-server" (str nrepl-port)]
                                                                *command-line-args*))
                                     (.redirectOutput java.lang.ProcessBuilder$Redirect/INHERIT))
                        proc       (.start pb)]
                    (wait/wait-for-port "localhost" nrepl-port)
                    (spit ".nrepl-port" nrepl-port)
                    (.deleteOnExit (File. ".nrepl-port"))
                    (.waitFor proc)))

(def WRAPPER
  "#!/usr/bin/env bash

# Generic wrapper to run a bb task from this project.
BB_TASK=\"$(basename \"$0\")\"
BB_CONFIG=\"$(cd \"$(dirname $(readlink --canonicalize \"$0\"))/..\" && pwd)/bb.edn\"
exec bb --config \"${BB_CONFIG}\" run \"$BB_TASK\" \"$@\"
")

(defn wrap
  "Generate a bin wrappers for one or more tasks."
  [& args]
  (let [bin     (-> "bin" fs/path str)
        wrapper (->> ".task-wrapper" (fs/path bin) str)]

    ;; Create bin directory if doesn't exist.
    (or (fs/exists? bin)
        (fs/create-dir bin))

    ;; Create the generic wrapper file.
    (spit wrapper WRAPPER)
    (fs/set-posix-file-permissions wrapper "rwxr-xr-x")

    ;; Create symlinks for each task to wrap.
    (doseq [task args
            :let [source (fs/path bin task)]]
      (println "Generating wrapper for" task)
      (fs/delete-if-exists source)
      (fs/create-sym-link source ".task-wrapper"))))

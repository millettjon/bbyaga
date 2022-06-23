(ns bbyaga.task
  (:require
   [babashka.fs        :as fs]
   [babashka.tasks     :refer [clojure]]
   [babashka.wait      :as wait]
   [bbyaga.task.test   :as test]
   [portal.api         :as p])
  (:refer-clojure :exclude [test]))

;; Ref: https://github.com/babashka/babashka/discussions/1122
;; -? how to have clojure load deps from bbyaga/deps.edn but still operate on local project?
;;    - can find directory if loaded from local root
;;    - shared bb fn that loads deps.edn from local root or jar?
;;    - ? how does jarvis do it?
;; -? dogfood: can it run oudated on iteself?
(defn outdated
  "Check for outdated dependencies."
  [& _args]
  (let [deps '{:deps {com.github.liquidz/antq {:mvn/version "RELEASE"}
                      org.slf4j/slf4j-nop     {:mvn/version "RELEASE"}}}]
    (clojure
     "-Sdeps" (str deps)
     "-M" "-m" "antq.core")))

;; -? how to run this?
;; -? add to bb --nrepl-server command line?
;; -? does bb look for user.clj?
;; -? pass preloads env to bb --nrepl-server?
;; (portal)
;; BABASHKA_PRELOADS="(require '[portal.api :as p]) (p/open)"
(defn portal
  []
  (p/open)
  (add-tap p/submit)
  (tap> ::hello))

;; TODO - ? add --portal as a command line option?
;; -? why does this not use babashka.process?
;; -? why does this take *command-line-args*?
;; -? is there a way for cider to send to portal?
(defn nrepl
  "Start an nrepl server."
  [& _args]
  (let [nrepl-port (with-open [sock (java.net.ServerSocket. 0)] (.getLocalPort sock))
        pb         (doto (ProcessBuilder. (into ["bb"
                                                 "--nrepl-server" (str nrepl-port)]
                                                *command-line-args*))
                     (-> .environment (.put "BABASHKA_PRELOADS" "((requiring-resolve 'bbyaga.task/portal))"))
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
  "Generate bin wrappers for one or more tasks."
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

(def test test/test)

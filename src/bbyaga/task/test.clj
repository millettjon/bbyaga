(ns bbyaga.task.test
  (:require
   [babashka.classpath :as cp]
   [babashka.fs        :as fs]
   [clojure.string     :as str]
   [clojure.test       :as t])
  (:refer-clojure :exclude [test]))

(defn clj->ns
  [file]
  (-> file
      fs/strip-ext
      (str/replace "_" "-")
      fs/components
      (->> (str/join "." ))
      symbol))

(defn test
  "Run tests"
  [& _]
  (let [test-dir "test"
        _        (cp/add-classpath test-dir)
        syms     (->> test-dir
                      (File.)
                      file-seq
                      (filter fs/regular-file?)
                      (mapv (partial fs/relativize test-dir))
                      (mapv clj->ns))]
    (->> syms
           (reduce (fn [m sym]
                     (require sym)
                     (assoc m sym (t/run-tests sym)))
                   {}))))
#_ (test)

;; TODO watch
;; https://github.com/babashka/pod-babashka-fswatcher

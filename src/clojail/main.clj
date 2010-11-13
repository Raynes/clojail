(ns clojail.main
  (:use [clojail core testers]
        clojure.contrib.command-line
        [clojure.stacktrace :only [root-cause print-stack-trace]])
  (:gen-class))

(defn -main [& args]
  (with-command-line args
    "clojail -- sandboxed Clojure evaluation"
    [[timeout "Set the amount of time it'll take for evaluation to timeout." 10000]
     [nojvm? "Turn off the JVM sandbox. Not a good idea."]
     [nostacktrace? "Turn off stack traces. Just print the root cause of all thrown exceptions."]
     [expr e "Expression to evaluate."]]
    (let [sb (sandbox secure-tester :timeout timeout :jvm? (not nojvm?))]
      (with-open [writer (java.io.StringWriter.)]
        (binding [*read-eval* false]
          (println
           (try
             (str writer (sb (read-string expr) {#'*out* writer}))
             (catch Exception e
               (if nostacktrace?
                 (-> e root-cause str)
                 (with-out-str (print-stack-trace (root-cause e))))))))))))
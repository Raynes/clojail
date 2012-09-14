(ns clojail.core
  (:use clojure.stacktrace
        [clojure.walk :only [walk postwalk-replace]]
        clojail.jvm)
  (:require serializable.fn)
  (:import (java.util.concurrent TimeoutException TimeUnit FutureTask)
           (clojure.lang LispReader$ReaderException)))

(defn eagerly-consume
  "Recursively force all lazy-seqs in val."
  [val]
  (try
    (postwalk-replace {} val)
    (catch Throwable _))
  val)

(def ^{:doc "Create a map of pretty keywords to ugly TimeUnits"}
  uglify-time-unit
  (into {} (for [[enum aliases] {TimeUnit/NANOSECONDS [:ns :nanoseconds]
                                 TimeUnit/MICROSECONDS [:us :microseconds]
                                 TimeUnit/MILLISECONDS [:ms :milliseconds]
                                 TimeUnit/SECONDS [:s :sec :seconds]}
                 alias aliases]
             {alias enum})))

(defn thunk-timeout
  "Takes a function and an amount of time to wait for thse function to finish
   executing. The sandbox can do this for you. unit is any of :ns, :us, :ms,
   or :s which correspond to TimeUnit/NANOSECONDS, MICROSECONDS, MILLISECONDS,
   and SECONDS respectively."
  ([thunk ms]
     (thunk-timeout thunk ms :ms)) ; Default to milliseconds, because that's pretty common.
  ([thunk time unit]
     (thunk-timeout thunk time unit identity))
  ([thunk time unit tg]
     (let [task (FutureTask. thunk)
           thr (if tg (Thread. tg task) (Thread. task))]
       (try
         (.start thr)
         (.get task time (or (uglify-time-unit unit) unit))
         (catch TimeoutException e
           (.cancel task true)
           (.stop thr) 
           (throw (TimeoutException. "Execution timed out.")))
         (catch Exception e
           (.cancel task true)
           (.stop thr) 
           (throw e))
         (finally (when tg (.stop tg)))))))

(defn safe-resolve
  "Resolves things safely."
  [s nspace]
  (try (if-let [resolved (ns-resolve nspace s)]
         resolved
         s)
       ;; Catching both of these exceptions appears to be necessary, because the exception that
       ;; is thrown appears to be different depending on which version of Clojure you use.
       (catch RuntimeException _ s)
       (catch ClassNotFoundException _ s)))

(defn flatten-all
  "The core flatten doesn't flatten maps."
  [form] (remove coll? (tree-seq coll? seq form)))

(defn- macroexpand-most
  "Macroexpand most, but not all. Leave non-collections and quoted things alone."
  [form]
  (if (or
       (not (coll? form)) 
       (and (seq? form) 
            (= 'quote (first form))))
    form
    (walk macroexpand-most identity (macroexpand form))))

(defn- separate
  "Take a collection and break it and its contents apart until we have
   a set of things to check for badness against."
  [s nspace]
  (set
   (flatten-all
    (map #(if (symbol? %)
            (let [resolved-s (safe-resolve % nspace)
                  s-meta (meta resolved-s)]
              (if s-meta
                [resolved-s ((juxt (comp symbol str :ns) :ns :name) s-meta)]
                (let [[bottom] (map symbol (.split (str %) "/"))
                      resolved-s (safe-resolve bottom nspace)]
                  (if (class? resolved-s)
                    [resolved-s %]
                    %))))
            %)
         (-> s macroexpand-most vector flatten-all)))))

(defn- dotify
  "Replace all . symbols with 'dot."
  [form]
  (if-not (coll? form)
    form
    (let [recurse #(walk dotify identity %)]
      (if-not (seq? form)
        (recurse form)
        (let [f (first form)]
          (case f
                quote form
                . (cons 'dot (recurse (rest form)))
                (recurse form)))))))

(def ^{:private true
       :doc "Fix code to make interop safe."}
  ensafen
  (comp dotify macroexpand-most))

(defn unsafe? [tester obj]
  (some #(% obj) tester))

(defn check-form
  "Check a form to see if it trips a tester."
  [form tester nspace]
  (some (partial unsafe? tester) (separate form nspace)))

(defn security-exception [problem]
  (throw
   (SecurityException.
    (format "You tripped the alarm! %s is bad!" problem))))

(defn- make-dot
  "Returns a safe . macro."
  [tester-sym]
  `(defmacro ~'dot [object# method# & args#]
     `(let [~'obj# ~object#
            ~'obj-class# (class ~'obj#)]
        (if-let [~'bad# (some (partial clojail.core/unsafe? ~~tester-sym)
                              [~'obj-class# ~'obj# (.getPackage ~'obj-class#)])]
          (clojail.core/security-exception ~'bad#)
          (. ~object# ~method# ~@args#)))))

(defn- user-defs
  "Find get a set of all the symbols of vars defined in a namespace."
  [nspace] (set (keys (ns-interns nspace))))

(defn- bulk-unmap
  "Unmap a bunch of vars."
  [nspace vars]
  (doseq [n vars]
    (binding [*ns* nspace]
      (eval `(ns-unmap *ns* '~n)))))

(defn- wipe-defs
  "Unmap vals in the sandbox only if the count of them is higher than max-defs."
  [init-defs old-defs max-defs nspace]
  (let [defs (remove init-defs (user-defs nspace))
        new-defs (remove old-defs defs)]
    (when (> (count defs) max-defs)
      (bulk-unmap nspace (remove init-defs old-defs)))
    (when (> (count new-defs) max-defs)
      (bulk-unmap nspace new-defs))))

(defn- evaluator [code tester-str context nspace transform bindings]
  (fn []
    (binding [*ns* nspace
              *read-eval* false]
      (let [bindings (or bindings {})
            tester-sym (gensym "tester")
            code `(do (def ~tester-sym (binding [*read-eval* true]
                                         (read-string ~tester-str)))
                       ~(make-dot tester-sym)
                       ~(ensafen code))]
        (with-bindings bindings (transform (jvm-sandbox #(eval code) context)))))))

(defn set-security-manager
  "Sets the system security manager to whatever you pass. Passing nil is
   the equivalent of turning it off entirely (which is usually how the JVM
   starts up)."
  [s] (System/setSecurityManager s))

(defn sandbox*
  "This function creates a sandbox function that takes a tester. A tester is a set of objects
   that you don't want to be allowed in code. It is a blacklist.

   Optional arguments are as follows:

   :timeout, default is 10000 MS or 10 seconds. If the expression evaluated in the sandbox
   takes longer than the timeout, an error will be thrown and the thread running the code
   will be stopped.

   :namespace, the namespace of the sandbox. The default is (gensym \"sandbox\").
   :context, the context for the JVM sandbox to run in. Only relevant if :jvm? is true. It
   has a sane default, so you shouldn't need to worry about this.

   :jvm?, if set to true, the JVM sandbox will be employed. It defaults to true.

   :transform, a function to call on the result returned from the sandboxed code, before
   returning it, while still within the timeout context.

   :init, some (quoted) code to run in the sandbox's namespace, but outside of the sandbox.

   :refer-clojure, true or false. If true (the default), automatically refer-clojure in the ns.
   You might want to set this to false at some point if you're working with the namespace in the
   :init key.

   This function will return a new function that you should bind to something. You can call
   this function with code and it will be evaluated in the sandbox. The function also takes
   an optional second parameter which is a hashmap of vars to values that will be passed to
   with-bindings. Since Clojure 1.3, only vars explicitly declared as dynamic are able to be
   rebound. As a result, only those vars will work here. If this doesn't work for you,
   read about the :init key."
  [& {:keys [timeout namespace context jvm transform
             init ns-init max-defs refer-clojure]
      :or {timeout 10000
           namespace (gensym "sandbox")
           context (-> (permissions) domain context)
           jvm true
           transform eagerly-consume
           refer-clojure true
           max-defs 5}}]
  (let [nspace (create-ns namespace)]
    (binding [*ns* nspace]
      (when refer-clojure (clojure.core/refer-clojure))
      (eval init))
    (let [init-defs (conj (user-defs nspace) 'dot)]
      (fn [code tester & [bindings]]
        (let [tester-str (pr-str tester)
              old-defs (user-defs nspace)]
          (when jvm (set-security-manager (SecurityManager.)))
          (try
            (if-let [problem (check-form code tester nspace)] 
              (security-exception problem)
              (thunk-timeout
               (evaluator code tester-str context nspace transform bindings)
               timeout
               :ms
               (ThreadGroup. "sandbox")))
            (finally (wipe-defs init-defs old-defs max-defs nspace))))))))

(defn sandbox
  "Convenience wrapper function around sandbox* to create a sandbox function out of a tester.
   Takes the same arguments as sandbox* with the addition of the tester argument. Returns a
   sandbox function like sandbox* returns, the difference being that the tester is hardcoded
   and doesn't need to be passed to the created function."
  [tester & args]
  (let [sb (apply sandbox* args)]
    #(apply sb % tester %&)))

(defn safe-read
  "Read a string from an untrusted source. Mainly just disables read-eval,
but also repackages thrown exceptions to make it easier to
discriminate among them. read-eval errors will be thrown as
IllegalStateException; other exceptions will be thrown unchanged."
  ([]
     (binding [*read-eval* false]
       (let [repackage (fn [e]
                         (let [msg (str (.getName (class e))
                                        ": "
                                        (.getMessage (root-cause e)))]
                           (if (.contains msg "EvalReader")
                             (IllegalStateException. msg)
                             e)))]
         (try
           (read)
           (catch LispReader$ReaderException e
             (throw (repackage e)))
           (catch Throwable e
             (let [cause (.getCause e)]
               (cond
                (not cause) (throw e)
                (not (instance? LispReader$ReaderException cause)) (throw e)
                :else (throw (repackage cause)))))))))
  ([str]
     (with-in-str str
       (safe-read))))


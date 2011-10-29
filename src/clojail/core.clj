;; Clojail is an easy way to sandbox your code. Whether you want to allow evaluation on a website,
;; in an IRC bot, or anything else you can think of, Clojail wants to be the easiest and most
;; comprehensive way to do that. But it isn't easy.
(ns clojail.core
  (:use clojure.stacktrace
        [clojure.walk :only [walk postwalk-replace]]
        clojail.jvm)
  (:import (java.util.concurrent TimeoutException TimeUnit FutureTask)
           (clojure.lang LispReader$ReaderException)))

;; postwalk is like a magical recursive doall, to force lazy-seqs
;; within the timeout context; but since it doesn't maintain perfect
;; structure for *every* data type, we want to actually return the
;; original value after we force it, not the result of postwalk
;; replacement
(defn eagerly-consume
  "Recursively force all lazy-seqs in val."
  [val]
  (try
    (postwalk-replace {} val)
    (catch Throwable _))
  val)

;; It sucks to have to deal with TimeUnits. They're so damned long.
(def uglify-time-unit "Create a map of pretty keywords to ugly TimeUnits"
  (into {} (for [[enum aliases] {TimeUnit/NANOSECONDS [:ns :nanoseconds]
                                 TimeUnit/MICROSECONDS [:us :microseconds]
                                 TimeUnit/MILLISECONDS [:ms :milliseconds]
                                 TimeUnit/SECONDS [:s :sec :seconds]}
                 alias aliases]
             {alias enum})))

;; This function uses some deprecated Java methods to stop threads, but the
;; reason they're deprecated doesn't really apply here. Just because people
;; don't use them properly doesn't mean they aren't useful.
;;
;; This function is useful in general, and that's why it is public.
(defn thunk-timeout
  "Takes a function and an amount of time to wait for the function to finish
   executing. The sandbox can do this for you. unit is any of :ns, :us, :ms,
   or :s which correspond to TimeUnit/NANOSECONDS, MICROSECONDS, MILLISECONDS,
   and SECONDS respectively."
  ([thunk ms]
     (thunk-timeout thunk ms :ms)) ; Default to milliseconds, because that's pretty common.
  ([thunk time unit]
     (thunk-timeout thunk time unit identity))
  ([thunk time unit transform]
     (thunk-timeout thunk time unit identity nil))
  ([thunk time unit transform tg]
     (let [task (FutureTask. (comp transform thunk))
           thr (if tg (Thread. tg task) (Thread. task))]
       (try
         (.start thr)
         (.get task time (or (uglify-time-unit unit) unit))
         (catch TimeoutException e
           (future-cancel task) ; Holy shit, we get to use a Clojure function!
           (.stop thr) 
           (throw (TimeoutException. "Execution timed out.")))
         (catch Exception e
           (.cancel task true)
           (.stop thr) 
           (throw e))
         (finally (when tg (.stop tg)))))))

(defn- separate
  "Take a collection and break it and its contents apart until we have
   a set of things to check for badness against."
  [s]
  (set
   (flatten
    (map #(if (symbol? %)
            (let [resolved-s (resolve %)]
              (if-let [s-meta (meta resolved-s)]
                ((juxt (comp symbol str :ns) :name) s-meta)
                (if (= Class (class resolved-s))
                  resolved-s
                  (-> % str (.split "/") (->> (map symbol))))))
            %)
         (flatten s)))))

(defn- collify
  "If form isn't a collection, wrap it in a vector."
  [form] (if (coll? form) form [form]))

(defn- macroexpand-most
  "Macroexpand most, but not all. Leave non-collections and quoted things alone."
  [form]
  (if (or
       (not (coll? form)) 
       (and (seq? form) 
            (= 'quote (first form))))
    form
    (walk macroexpand-most identity (macroexpand form))))

;; Because the dot (.) interop form is a special form, we can't just rebind it or anything.
;; Instead, we need to replace it entirely with a safe macro of our own. To do this, we need
;; to replace all . symbols with 'dot', the name of our own safe dot macro.
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

;; Compose our earlier functions.
(def ^{:private true} ensafen "Fix code to make interop safe."
  (comp dotify macroexpand-most))

(def ^{:private true} mutilate
  "Macroexpand and separate pieces to create a set of symbols and such
   that is easy to check for badness."
  (comp separate collify macroexpand-most))

;; The clojail equivalent of motion detectors.
(defn check-form
  "Check a form to see if it trips a tester."
  [form tester]
  (let [mutilated (mutilate form)]
    (if (set? tester)
      (some tester mutilated)
      (let [{:keys [whitelist blacklist]} tester]
        (or (some #(and (symbol? %) whitelist (not (whitelist %)) %) mutilated)
            (and blacklist (some blacklist mutilated)))))))

;; We have to run the sandbox against packages as well as classes,
;; but macros can't embed Package objects in code by default. This
;; is a simple print-dup method so that we can embed them in our dot
;; macro.
(defmethod print-dup java.lang.Package
  ([p out]
     (.write out (str "#=(java.lang.Package/getPackage \""
                      (.getName p)
                      "\")"))))

(defn- make-dot
  "Returns a safe . macro."
  [tester-str]
  `(defmacro ~'dot [object# method# & args#]
     `(let [~'tester-obj# (binding [*read-eval* true]
                            (read-string ~~tester-str))
            ~'tester-fn# (if (map? ~'tester-obj#)
                           (let [{~'blacklist# :blacklist,
                                  ~'whitelist# :whitelist} ~'tester-obj#]
                             (fn [~'target#]
                               (or (and ~'whitelist# (not (~'whitelist# ~'target#)) ~'target#)
                                   (and ~'blacklist# (~'blacklist# ~'target#)))))
                           ~'tester-obj#)
            ~'obj# ~object#
            ~'obj-class# (class ~'obj#)]
        (if-let [~'bad#
                 (some ~'tester-fn#
                       [~'obj-class#
                        ~'obj#
                        (.getPackage ~'obj-class#)])]
          (throw (SecurityException. (str "You tripped the alarm! " ~'bad# " is bad!")))
          (. ~object# ~method# ~@args#)))))

(defn- set-security-manager [s] (System/setSecurityManager s))

(defn- user-defs
  "Find get a set of all the symbols of vars defined in a namespace."
  [nspace] (set (keys (ns-interns nspace))))

(defn- wipe-defs
  "Unmap vals in the sandbox only if the count of them is max-defs."
  [init-defs old-defs max-defs nspace]
  (let [defs (remove init-defs (user-defs nspace))]
    (when (> (count defs) max-defs)
      (doseq [n (remove init-defs old-defs)]
        (binding [*ns* nspace]
          (eval `(ns-unmap *ns* '~n)))))))

(defn sandbox*
  "This function creates a sandbox function that takes a tester. A tester can either be
   a plain set of symbols, in which case it'll be treated as a blacklist. Otherwise, you
   can provide a map of :whitelist and :blacklist bound to sets. In this case, the whitelist
   and blacklist will both be used. If you only want a whitelist, just supply :whitelist in
   the map.

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

   :ns-init, a sequence of calls to things like refer-clojure, require, use, etc. It should
   look like this: :ns-init `((refer-clojure) (require foo) ...). If this key isn't passed,
   the ns is set up by calling refer-clojure. If you pass this key, you should always pass
   refer-clojure first.

   This function will return a new function that you should bind to something. You can call
   this function with code and it will be evaluated in the sandbox. The function also takes
   an optional second parameter which is a hashmap of vars to values that will be passed to
   with-bindings. Since Clojure 1.3, only vars explicitly declared as dynamic are able to be
   rebound. As a result, only those vars will work here. If this doesn't work for you,
   read about the :init key.

   Example: (def sb (sandbox #{'alter-var-root 'java.lang.Thread} :timeout 5000))
            (let [writer (java.io.StringWriter.)]
              (sb '(println \"blah\") {#'*out* writer}) (str writer))
   The above example returns \"blah\\n\""
  [& {:keys [timeout namespace context jvm? transform init ns-init max-defs]
      :or {timeout 10000
           namespace (symbol (gensym "sandbox"))
           context (-> (empty-perms-list) domain context)
           jvm? true
           transform eagerly-consume
           ns-init [`(refer-clojure)]
           max-defs 5}}]
  (when jvm? (set-security-manager (SecurityManager.)))
  (let [nspace (create-ns namespace)]
    (binding [*ns* nspace]
      (eval `(do ~@ns-init
                 ~init)))
    (let [init-defs (conj (user-defs nspace) 'dot)]
      (fn [tester code & [bindings]]
        (let [tester-str (with-out-str (binding [*print-dup* true] (pr tester)))
              old-defs (user-defs nspace)]
          (try
            (if-let [problem (check-form code tester)]
              (throw (SecurityException. (str "You tripped the alarm! " problem " is bad!")))
              (thunk-timeout
               (fn []
                 (binding [*ns* nspace
                           *read-eval* false]
                   (let [bindings (or bindings {})
                         code `(do ~(make-dot tester-str)
                                   ~(ensafen code))]
                     (with-bindings bindings (jvm-sandbox #(eval code) context)))))
               timeout :ms transform (ThreadGroup. "sandbox")))
            (finally (wipe-defs init-defs old-defs max-defs nspace))))))))

(defn sandbox
  "Convenience wrapper function around sandbox* to create a sandbox function out of a tester.
   Takes the same arguments as sandbox* with the addition of the tester argument. Returns a
   sandbox function like sandbox* returns, the difference being that the tester is hardcoded
   and doesn't need to be passed to the created function."
  [tester & args]
  (let [sb (apply sandbox* args)]
    (partial sb tester)))

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

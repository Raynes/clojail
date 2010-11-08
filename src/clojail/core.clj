(ns ^{:doc "Defines simple Clojure sandboxing functionality."}
  clojail.core
  (:use [clojure.walk :only [macroexpand-all]]
        clojail.jvm)
  (:import (java.util.concurrent TimeoutException TimeUnit FutureTask)))

(defn enable-security-manager
  "Enable the JVM security manager. The sandbox can do this for you."
  [] (System/setSecurityManager (SecurityManager.)))

(defn thunk-timeout
  "Takes a function and an amount of time in ms to wait for the function to finish
  executing. The sandbox can do this for you."
  [thunk seconds]
  (let [task (FutureTask. thunk)
        thr (Thread. task)]
    (try
      (.start thr)
      (.get task seconds TimeUnit/MILLISECONDS)
          (catch TimeoutException e
            (.cancel task true)
            (.stop thr (Exception. "Thread stopped!")) 
            (throw (TimeoutException. "Execution timed out.")))
	  (catch Exception e
	    (.cancel task true)
	    (.stop thr (Exception. "Thread stopped!")) 
	    (throw e)))))

(defn- separate [s]
  (flatten
   (map
    #(if (symbol? %)
       (if-let [s-meta (-> % resolve meta)]
         ((juxt (comp symbol str :ns) :name) s-meta)
         (-> % str (.split "/") (->> (map symbol))))
       %)
    s)))

(defn- collify [form] (if (coll? form) form [form]))

(def ^:private mutilate (comp separate collify macroexpand-all))

(defn- check-form [form sandbox-set]
  (some sandbox-set (mutilate form)))

(defn- dotify [code]
  (cond
   (coll? code)
   (map #(cond (= % '.) 'dot
               (coll? %) (dotify %)
               :else %)
        (macroexpand-all code))
   (= '. code) 'dot
   :else code))

(declare tester)

(defn- when-push [bindings code]
  (if bindings
    (try (push-thread-bindings bindings)
         (code)
         (finally (pop-thread-bindings)))
    (code)))

(defn sandbox
  "This function creates a new sandbox from a tester (a set of symbols that make up a blacklist)
   and optional arguments.

   Optional arguments are as follows:
   :timeout, default is 10000 MS or 10 seconds. If the expression evaluated in the sandbox takes
   longer than the timeout, an error will be thrown and the thread running the code will be stopped.
   :namespace, the namespace of the sandbox. The default is (gensym \"sandbox\").
   :context, the context for the JVM sandbox to run in. Only relevant if :jvm? is true. It has a sane
   default, so you shouldn't need to worry about this.
   :jvm?, if set to true, the JVM sandbox will be employed. It defaults to true.

   This function will return a new function that you should bind to something. You can call this
   function with code and it will be evaluated in the sandbox. The function also takes an optional
   second parameter which is a hashmap of vars to values that will be passed to push-thread-bindings.

   Example: (def sb (sandbox #{'alter-var-root 'java.lang.Thread} :timeout 5000))
            (let [writer (java.io.StringWriter.)]
              (sb '(println \"blah\") {#'*out* writer}) (str writer))
   The above example returns \"blah\\n\""
  [tester & {:keys [timeout namespace context jvm?]
             :or {timeout 10000 namespace (gensym "sandbox")
                  context (-> (empty-perms-list) domain context) jvm? true}}]
  (when jvm? (enable-security-manager))
  (fn [code & [bindings]]
    (if-let [problem (check-form code tester)]
      (throw (SecurityException. (str "You tripped the alarm! " problem " is bad!")))
      (thunk-timeout
       (fn []
         (binding [*ns* (create-ns namespace)
                   *read-eval* false
                   tester tester]
           (refer 'clojure.core)
           (eval
            '(defmacro dot [object method & args]
               (if (not
                    (or (-> object class pr-str symbol clojail.core/tester)
                        (-> object pr-str symbol resolve pr-str symbol clojail.core/tester)
                        (clojail.core/tester method)))
                 `(. ~object ~method ~@args)
                 (throw
                  (SecurityException.
                   (str "Tried to call " method " on " object ". This is not allowed."))))))
           (when-push bindings #(jvm-sandbox (fn [] (eval (dotify code))) context))))
       timeout))))

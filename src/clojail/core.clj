(ns clojail.core
  (:use [clojure.walk :only [macroexpand-all]]
        clojail.jvm)
  (:import (java.util.concurrent TimeoutException TimeUnit FutureTask)))

(defn enable-security-manager []
  (System/setSecurityManager (SecurityManager.)))

(defn thunk-timeout [thunk seconds]
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

(defn separate [s]
  (flatten
   (map
    #(if (symbol? %)
       (if-let [s-meta (-> % resolve meta)]
         ((juxt (comp symbol str :ns) :name) s-meta)
         (-> % str (.split "/") (->> (map symbol))))
       %)
    s)))

(defn collify [form] (if (coll? form) form [form]))

(def mutilate (comp separate collify macroexpand-all))

(defn check-form [form sandbox-set]
  (some sandbox-set (mutilate form)))

(defn dotify [code]
  (cond
   (coll? code)
   (map #(cond (= % '.) 'dot
               (coll? %) (dotify %)
               :else %)
        (macroexpand-all code))
   (= '. code) 'dot
   :else code))

(declare tester)

(defn sandbox [tester & {:keys [timeout namespace context jvm?]
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
           (when bindings (push-thread-bindings bindings))
           (jvm-sandbox
            (fn [] (try (eval (dotify code)) (finally (pop-thread-bindings))))
            context)))
       timeout))))

(ns clojail.core
  (:use [clojure.walk :only [macroexpand-all]])
  (:import (java.util.concurrent TimeoutException TimeUnit FutureTask)))

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

(def mutilate (comp separate macroexpand-all))

(defn check-form [form sandbox-set]
  (some sandbox-set (mutilate form)))

(defn dotify [code]
  (map #(cond (= % '.) 'dot
              (coll? %) (dotify %)
              :else %)
       (macroexpand-all code)))

(declare tester)

(defn sandbox [tester & {:keys [timeout namespace]
                         :or {timeout 10000 namespace (gensym "sandbox")}}]
  (fn [code]
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
           (eval (dotify code))))
       timeout))))

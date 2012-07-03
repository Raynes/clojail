(ns clojail.testers
  "A set of predefined testers that you can use in your own sandboxes.
   I'm not promising that any of these are really totally secure. I cannot
   possibly test these for everything."
  (:require [bultitude.core :as nses]
            [serializable.fn :as sfn]))

(defn p
  "Create a package object for putting in a tester."
  [s] (Package/getPackage s))

(defn prefix-checker [n]
  (sfn/fn [s]
          (when (symbol? s)
            (.startsWith (name s) (str n)))))

(defn suffix-tester [n]
  (sfn/fn [s]
          (when (symbol? s)
            (.endsWith (name s) (munge (str "$" n))))))

(defn blacklist-ns
  "Blacklist a Clojure namespace."
  [tester n]
  (conj tester n (prefix-checker n)))

(defn blacklist-symbols
  "Blacklist symbols."
  [tester & symbols]
  (into tester (concat symbols (map suffix-tester symbols))))

(defn blacklist-packages
  "Blacklist a bunch of Java packages at once."
  [tester & packages]
  (into tester (map p packages)))

(defn blanket
  "Takes a tester and some namespace prefixes as strings. Looks up
   the prefixes with bultitude, getting a list of all namespaces on
   the classpath matching those prefixes."
  [tester & prefixes]
  (reduce blacklist-ns tester
          (mapcat (partial nses/namespaces-on-classpath :prefix) prefixes)))

(def ^{:doc "A tester that attempts to be secure, and allows def."}
  secure-tester-without-def
  (-> #{clojure.lang.Compiler clojure.lang.Ref clojure.lang.Reflector
        clojure.lang.Namespace 'System/out 'System/in 'System/err
        clojure.lang.Var}
      (blacklist-packages "java.lang.reflect"
                          "java.security"
                          "java.util.concurrent"
                          "java.awt")
      (blacklist-symbols
       'alter-var-root 'intern 'eval 'catch 
       'load-string 'load-reader 'addMethod 'ns-resolve 'resolve 'find-var
       '*read-eval* 'ns-publics 'ns-unmap 'set! 'ns-map 'ns-interns 'the-ns
       'push-thread-bindings 'pop-thread-bindings 'future-call 'agent 'send
       'send-off 'pmap 'pcalls 'pvals 'in-ns 'System/out 'System/in 'System/err
       'with-redefs)
      (blanket "clojail")))

(def ^{:doc "A somewhat secure tester. No promises."}
  secure-tester
  (conj secure-tester-without-def 'def))
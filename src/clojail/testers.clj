(ns clojail.testers
  "A set of predefined testers that you can use in your own sandboxes.
   I'm not promising that any of these are really totally secure. I cannot
   possibly test these for everything."
  (:require [bultitude.core :as nses]
            [serializable.fn :as sfn]))

(deftype ClojailPackage [package])

(defmethod print-method ClojailPackage
  [p out]
  (.write out (str "#=(clojail.testers/->ClojailPackage \""
                   (.package p)
                   "\")")))

(defn p
  "Create package objects for putting in a tester."
  [& packages]
  (map #(->ClojailPackage %) packages))

(defn blacklist-nses
  "Blacklist Clojure namespaces. nses should be a collection of namespaces."
  [nses]
  (sfn/fn [s]
          (first (filter #(or (= s %)
                              (when (symbol? s)
                                (.startsWith (name s) (str %))))
                         nses))))

(defn blacklist-symbols
  "Blacklist symbols. Second argument should be a set of symbols."
  [symbols]
  (sfn/fn [s]
          (when (symbol? s)
            (first (filter #(or (= s %)
                                (.endsWith (name s) (munge (str "$" %))))
                           symbols)))))

(defn blacklist-packages
  "Blacklist packages. packages should be a collection of ClojailPackage objects.
  These can be created with the p function."
  [packages]
  (sfn/fn [obj]
          (let [obj (if (= Class (type obj))
                      (.getPackage obj)
                      obj)]
            (when obj
              (first (filter #(let [pack (.package %)]
                                (or (= obj (Package/getPackage pack))
                                    (= obj (symbol pack))))
                             packages))))))

(defn blacklist-objects
  "Blacklist some objects. objs should be a collection of things."
  [objs]
  (sfn/fn [s] (first (filter #(= s %) objs))))

(defn blanket
  "Takes a tester and some namespace prefixes as strings. Looks up
  the prefixes with bultitude, getting a list of all namespaces on
  the classpath matching those prefixes."
  [& prefixes]
  (blacklist-nses (mapcat (partial nses/namespaces-on-classpath :prefix) prefixes)))

(def ^{:doc "A tester that attempts to be secure, and allows def."}
  secure-tester-without-def
  [(blacklist-objects [clojure.lang.Compiler clojure.lang.Ref clojure.lang.Reflector
                       clojure.lang.Namespace clojure.lang.Var clojure.lang.RT])
   (blacklist-packages (p "java.lang.reflect"
                          "java.security"
                          "java.util.concurrent"
                          "java.awt"))
   (blacklist-symbols
    '#{alter-var-root intern eval catch 
       load-string load-reader addMethod ns-resolve resolve find-var
       *read-eval* ns-publics ns-unmap set! ns-map ns-interns the-ns
       push-thread-bindings pop-thread-bindings future-call agent send
       send-off pmap pcalls pvals in-ns System/out System/in System/err
       with-redefs})
   (blanket "clojail")])

(def ^{:doc "A somewhat secure tester. No promises."}
  secure-tester
  (conj secure-tester-without-def (blacklist-symbols '#{def})))
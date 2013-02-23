(ns clojail.testers
  "A set of predefined testers that you can use in your own sandboxes.
   I'm not promising that any of these are really totally secure. I cannot
   possibly test these for everything."
  (:require [bultitude.core :as nses]
            [serializable.fn :as sfn]))

(deftype ClojailWrapper [object])

(defmethod print-method ClojailWrapper
  [p out]
  (.write out (str "#clojail.testers.ClojailWrapper["
                   (binding [*print-dup* true] (pr-str (.object p)))
                   "]")))

(defn wrap-object
  "Wraps an object in the ClojailWrapper type to be passed into the tester."
  [object]
  (if (instance? ClojailWrapper object)
    object
    (->ClojailWrapper object)))

(defn blacklist-nses
  "Blacklist Clojure namespaces. nses should be a collection of namespaces."
  [nses]
  (let [nses (map wrap-object nses)]
    (sfn/fn [s]
            (when-let [result (first (filter #(let [n (.object %)]
                                                (or (= s n)
                                                    (when (symbol? s)
                                                      (.startsWith (name s) (str n)))))
                                             nses))]
              (.object result)))))

(defn blacklist-symbols
  "Blacklist symbols. Second argument should be a set of symbols."
  [symbols]
  (sfn/fn [s]
          (when (symbol? s)
            (first (filter #(or (= s %)
                                (.endsWith (name s) (munge (str "$" %))))
                           symbols)))))

(defn blacklist-packages
  "Blacklist packages. Packages should be a list of strings corresponding to
   packages."
  [packages]
  (let [packages (map wrap-object packages)]
    (sfn/fn [obj]
            (let [obj (if (= Class (type obj))
                        (.getPackage obj)
                        obj)]
              (when obj
                (first (filter #(let [pack (.object %)]
                                  (or (= obj (Package/getPackage pack))
                                      (= obj (symbol pack))))
                               packages)))))))

(defn blacklist-objects
  "Blacklist some objects. objs should be a collection of things."
  [objs]
  (let [objs (map wrap-object objs)]
    (sfn/fn [s]
            (when-let [result (first (filter #(= s (.object %)) objs))]
              (.object result)))))

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
   (blacklist-packages ["java.lang.reflect"
                        "java.security"
                        "java.util.concurrent"
                        "java.awt"])
   (blacklist-symbols
    '#{alter-var-root intern eval catch 
       load-string load-reader addMethod ns-resolve resolve find-var
       *read-eval* ns-publics ns-unmap set! ns-map ns-interns the-ns
       push-thread-bindings pop-thread-bindings future-call agent send
       send-off pmap pcalls pvals in-ns System/out System/in System/err
       with-redefs-fn})
   (blacklist-nses '[clojure.main])
   (blanket "clojail")])

(def ^{:doc "A somewhat secure tester. No promises."}
  secure-tester
  (conj secure-tester-without-def (blacklist-symbols '#{def})))
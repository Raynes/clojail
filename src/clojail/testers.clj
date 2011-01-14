(ns ^{:doc "A set of predefined testers that you can use in your own sandboxes. I'm not promising that
            any of these are really totally secure. I cannot possibly test these for everything."}
  clojail.testers)

(defn p
  "Create a package object for putting in a tester."
  [s] (Package/getPackage s))

(def ^{:doc "A somewhat secure tester. No promises."}
     secure-tester
     #{'alter-var-root 'intern 'def 'eval 'catch
       'load-string 'load-reader 'addMethod 'ns-resolve 'resolve 'find-var
       '*read-eval* clojure.lang.Ref clojure.lang.Reflector 'ns-publics
       'ns-unmap 'set! 'ns-map 'ns-interns 'the-ns clojure.lang.Namespace
       'push-thread-bindings 'pop-thread-bindings
       (p "java.lang.reflect")})
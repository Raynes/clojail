(ns clojail.testers
  "A set of predefined testers that you can use in your own sandboxes.
   I'm not promising that any of these are really totally secure. I cannot
   possibly test these for everything.")

(defn p
  "Create a package object for putting in a tester."
  [s] (Package/getPackage s))

(def secure-tester-without-def "A tester that attempts to be secure, and allows def."
  #{'alter-var-root 'intern 'eval 'catch
    'load-string 'load-reader 'addMethod 'ns-resolve 'resolve 'find-var
    '*read-eval* clojure.lang.Ref clojure.lang.Reflector 'ns-publics
    'ns-unmap 'set! 'ns-map 'ns-interns 'the-ns clojure.lang.Namespace
    'push-thread-bindings 'pop-thread-bindings 'future-call 'agent 'send
    'send-off 'pmap 'pcalls 'pvals 'in-ns
    (p "java.lang.reflect")
    (p "java.util.concurrent")
    (p "java.awt")})

(def secure-tester "A somewhat secure tester. No promises."
  (conj secure-tester-without-def 'def))
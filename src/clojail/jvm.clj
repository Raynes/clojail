(ns clojail.jvm)

; Java Sandbox Stuff, this is from the clojurebot code
; http://github.com/hiredman/clojurebot/blob/master/src/hiredman/sandbox.clj

(defn empty-perms-list []
      (doto (java.security.Permissions.)
        (.add (RuntimePermission. "accessDeclaredMembers"))))


(defn domain [perms]
     (java.security.ProtectionDomain.
       (java.security.CodeSource. nil
                                  (cast java.security.cert.Certificate nil))
       perms))
 
(defn context [dom]
      (java.security.AccessControlContext. (into-array [dom])))
 
(defn priv-action [thunk]
      (proxy [java.security.PrivilegedAction] [] (run [] (thunk))))
 
(defn sandbox
  [thunk context]
      (java.security.AccessController/doPrivileged
        (priv-action thunk)
        context))

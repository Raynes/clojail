(ns clojail.jvm)

(defn empty-perms-list []
  (doto (java.security.Permissions.)
    (.add (RuntimePermission. "accessDeclaredMembers"))))


(defn domain [perms]
  (java.security.ProtectionDomain.
   (java.security.CodeSource. nil
                              (cast java.security.cert.Certificate nil))
   perms))

(defn context [& dom]
  (java.security.AccessControlContext. (into-array dom)))

(defn priv-action [thunk]
  (proxy [java.security.PrivilegedAction] [] (run [] (thunk))))

(defn jvm-sandbox
  [thunk context]
  (java.security.AccessController/doPrivileged
   (priv-action thunk)
   context))
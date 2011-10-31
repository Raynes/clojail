(ns clojail.jvm)

(defn empty-perms-list
  "Create an empty list of permissions. All it permisses is the ability to
   access declared members."
  []
  (doto (java.security.Permissions.)
    (.add (RuntimePermission. "accessDeclaredMembers"))))

(defn domain
  "Create a protection domain out of permissions."
  [perms]
  (java.security.ProtectionDomain.
   (java.security.CodeSource.
    nil
    (cast java.security.cert.Certificate nil))
   perms))

(defn context
  "Create an access control context out of domains."
  [& dom]
  (java.security.AccessControlContext. (into-array dom)))

(defn priv-action
  "Create a one-off privileged action with a run method that just runs the
   function you pass."
  [thunk]
  (proxy [java.security.PrivilegedAction] [] (run [] (thunk))))

(defn jvm-sandbox
  "Run a function inside of a security context."
  [thunk context]
  (java.security.AccessController/doPrivileged
   (priv-action thunk)
   context))
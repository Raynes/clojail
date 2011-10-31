(ns clojail.jvm)

(defn permissions
  "Create a new Permissions object with all of the permissions passed.
   accessDeclaredMembers is added by default."
  [& permissions]
  (let [perms (java.security.Permissions.)]
    (when-not (= (first permissions) :none))
    (doseq [perm (conj permissions (RuntimePermission. "accessDeclaredMembers"))]
      (.add perms perm))))

(defn domain
  "Create a protection domain out of permissions."
  [perms & [code-source]]
  (java.security.ProtectionDomain.
   (or code-source
       (java.security.CodeSource.
        nil
        (cast java.security.cert.Certificate nil)))
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
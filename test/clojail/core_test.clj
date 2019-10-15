(ns clojail.core-test
  (:use [clojail core testers jvm]
        clojure.test)
  (:require clojure.set) ;; For testing.
  (:import java.io.StringWriter
           java.util.concurrent.ExecutionException))

(def sb (sandbox secure-tester))

(def easy (sandbox []))

(deftest dot-test
  (is (= 4 (easy '(. "dots" (length))))))

(deftest dot-shorthand-test
  (is (= true (easy '(= (.length "string") 6)))))

(deftest security-test
  (is (= 7 (sb '(-> "bar.txt" java.io.File. .getName .length))))
  (is (thrown? Exception (sb '(-> java.io.File .getMethods (aget 0) .getName))))
  (is (thrown? Exception (sb '(-> java.io.File .getMethods (aget 0) ((memfn getName))))))
  (is (thrown? Exception (sb '((clojure.lang.Compiler/maybeResolveIn *ns* (symbol "eval")) '(+ 3 3)))))
  (is (thrown? Exception (sb '(inc (clojure.core/eval 1))))))

(deftest sandbox-config-test
  (is (string? (easy '(-> java.io.File .getMethods (aget 0) .getName)))))

(deftest lazy-dot-test
  (is (= [0 0] (sb '(map #(.length %) ["" ""])))))

(deftest binding-test
  (is (= 2 (sb '(#'*out* 2) {#'*out* identity}))))

(deftest macroexpand-test
  (is (= 'let (sb '(first '(let [x 1] x)))))
  (is (= '(dec (inc x))
         (sb '(macroexpand '(-> x inc dec)))))
  (is (= 1 (sb '(-> 0 inc dec inc))))
  (is (= '(. "" length) (sb ''(. "" length)))))

(deftest macroexpand-most-test
  (is (= (range 1 11) (sb '(->> (inc x)
                                (for [x (range 0 10)]))))))

(deftest dynamic-tester-test
  (let [dyn-sb (sandbox*)
        code '(+ 5 5)]
    (is (= 10 (dyn-sb code [])))
    (is (thrown? SecurityException (dyn-sb code [(blacklist-symbols '#{+})])))
    (is (thrown? SecurityException (dyn-sb 'clojure.core/eval [(blacklist-symbols '#{eval})])))))

(deftest namespace-forbid-test
  (let [sb (sandbox [(blacklist-nses [(the-ns 'clojure.core)])])]
    (is (thrown? SecurityException (sb '(+ 1 2))))))

(deftest init-test
  (let [sb (sandbox secure-tester :init '(do (use 'clojure.set) (def foo 1)))]
    (is (= 1 (sb 'foo)))
    (is (= clojure.set/rename-keys (sb 'rename-keys)))))

(defn def-forms [symbols]
  (map #(list 'def % 0) symbols))

(deftest def-test
  (let [sb-one (sandbox secure-tester-without-def)
        sb-two (sandbox secure-tester-without-def)
        sb-three (sandbox secure-tester-without-def :init '(def foo 0))]
    (testing "Leaves new defs if they're less than max def."
      (doseq [form (def-forms '[q w e r t y u i])]
        (sb-one form))
      (is (thrown-with-msg? ExecutionException #"Syntax error" (sb-one 't)))
      (is (= 0 (sb-one 'i))))
    (testing "Destroys old *and* new defs if new defs is also over max-def."
      (sb-two (cons 'do (map #(list 'def % 0) '[a b c d e f])))
      (is (thrown-with-msg? ExecutionException #"Syntax error" (sb 'f))))
    (testing "Leaves init defs."
      (doseq [form (def-forms '[q w e r t y])]
        (sb-three form))
      (is (= 0 (sb-three 'foo))))))

(deftest with-redefs-regression
  (testing "Complete breakage of subsequent tests if with-redefs is allowed"
    (let [sb (sandbox secure-tester)]
      (is (thrown-with-msg? SecurityException #"is bad!"
            (sb '(with-redefs [let #'fn] (inc 4))))))))

(deftest require-test
  (let [sb (sandbox secure-tester)]
    (is (nil? (sb '(require 'clojure.string))))))

(deftest security-off-test
  (let [sb (sandbox secure-tester :jvm false)]
    (set-security-manager nil)
    (is (= "foo\n" (sb '(slurp "test/test.txt"))))))

(deftest block-fields-test
  (let [sb (sandbox secure-tester)]
    (doseq [field '[System/out System/in System/in]]
      (is (thrown-with-msg? SecurityException #"is bad!" (sb `(. ~field println "foo")))))))

(deftest custom-context-test
  (let [sb (sandbox secure-tester :context (-> (java.io.FilePermission. "foo" "read,write,delete")
                                               permissions
                                               domain
                                               context))]
    (is (nil? (sb '(spit "foo" "Hi!"))))
    (is (= "Hi!" (sb '(slurp "foo"))))
    (is (true? (sb '(.delete (java.io.File. "foo")))))
    (is (thrown-with-msg? ExecutionException #"access denied" (sb '(spit "foo2" "evil"))))))

(deftest block-specific-test
  (let [sb (sandbox [(blacklist-objects [#'clojure.core/+])] :init '(def + 3))]
    (is (thrown-with-msg? SecurityException #"You tripped the alarm!"
          (sb '(clojure.core/+ 3 3))))
    (is (= 3 (sb '+)))))

(deftest block-maps
  (let [sb (sandbox secure-tester)]
    (is (thrown? SecurityException (sb '{:foo (eval '(+ 3 3))})))))

(deftest blanket-test
  (let [sb (sandbox [(blanket "clojail")])]
    (is (thrown? SecurityException
                 (sb '(clojail.jvm/priv-action "this wont work anyways so why would I write something meaningful."))))
    (is (thrown? SecurityException
                 (sb '(.invoke (clojail.jvm$jvm_sandbox.) (fn [] 0) nil))))))

(deftest meta-meta-meta-test
  (let [sb (sandbox secure-tester)]
    (is (thrown? SecurityException
                 (sb '(java.security.AccessController/doPrivileged
                       (reify java.security.PrivilegedExceptionAction
                         (run [_] (slurp (.getInputStream  (.exec (Runtime/getRuntime) "whoami")))))))))
    (is (thrown? SecurityException
                 (sb '(java.security.AccessController/doPrivileged
                       (reify java.security.PrivilegedAction
                         (run [_] (slurp (.getInputStream  (.exec (Runtime/getRuntime) "whoami")))))))))))

(deftest blacklist-symbol-classes
  (let [sb (sandbox [(blacklist-symbols '#{eval})])]
    (is (thrown? SecurityException
                 (sb '(.invoke (clojure.core$eval.) '(+ 3 3)))))))

(deftest class-blacklist
  (let [sb (sandbox secure-tester)]
    (is (thrown-with-msg? java.util.concurrent.ExecutionException #"Namespace"
                 (sb '(((.getMappings
                         (first
                          (filter #(= (.name %) 'clojure.core)
                                  (all-ns))))
                        (symbol "eval"))
                       (read-string "(+ 1 2)")))))))

(deftest serialization-test
  (let [sb (sandbox secure-tester)]
    (is (thrown-with-msg? Exception #"alarm"
          (sb '(-> "ACED000573720011636C6F6A7572652E636F7265246576616C4AFAD1663CDD43B502000078720016636C6F6A7572652E6C616E672E4146756E6374696F6E3E06709C9E46FDCB0200014C00115F5F6D6574686F64496D706C436163686574001E4C636C6F6A7572652F6C616E672F4D6574686F64496D706C43616368653B787070"
                   javax.xml.bind.DatatypeConverter/parseHexBinary
                   java.io.ByteArrayInputStream.
                   java.io.ObjectInputStream.
                   .readObject)
              '(+ 1 2))))))

#_(deftest fast-enough
  (let [sb (sandbox secure-tester)]
    (are [form] (= (eval form) (sb form))
         '(str (for [x (range 1000000)]
                 x))
         '(dotimes [n 1000000]
            (Math/ceil n)))))

(deftest laziness-test
  (let [sb (sandbox secure-tester)]
    (is (thrown-with-msg? Exception #"access denied"
          (sb '(map slurp ["project.clj"]))))))

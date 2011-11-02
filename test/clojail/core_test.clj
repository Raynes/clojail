(ns clojail.core-test
  (:use [clojail core testers jvm]
        clojure.test)
  (:import java.io.StringWriter
           java.util.concurrent.ExecutionException))

(def sb (sandbox secure-tester))
(def easy (sandbox #{}))

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
  (is (= '(dec (clojure.core/-> x inc))
         (sb '(macroexpand '(-> x inc dec)))))
  (is (= 1 (sb '(-> 0 inc dec inc))))
  (is (= '(. "" length) (sb ''(. "" length)))))

;; make sure macros are expanded outside-in, not inside-out
(deftest macroexpand-most-test
  (is (= (range 1 11) (sb '(->> (inc x)
                                (for [x (range 0 10)]))))))

;; sandbox* lets you change tester on the fly
(deftest dynamic-tester-test
  (let [dyn-sb (sandbox*)
        code '(+ 5 5)]
    (is (= 10 (dyn-sb code #{})))
    (is (thrown? SecurityException (dyn-sb code '#{+})))
    (is (thrown? SecurityException (dyn-sb 'clojure.core/eval '#{eval})))))

(deftest namespace-forbid-test
  (let [sb (sandbox #{'clojure.core})]
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
      (is (thrown-with-msg? ExecutionException #"Unable to resolve symbol" (sb-one 't)))
      (is (= 0 (sb-one 'i))))
    (testing "Destroys old *and* new defs if new defs is also over max-def."
      (sb-two (cons 'do (map #(list 'def % 0) '[a b c d e f])))
      (is (thrown-with-msg? ExecutionException #"Unable to resolve symbol" (sb 'f))))
    (testing "Leaves init defs."
      (doseq [form (def-forms '[q w e r t y])]
        (sb-three form))
      (is (= 0 (sb-three 'foo))))))

(deftest require-test
  (let [sb (sandbox secure-tester)]
    (is (nil? (sb '(require 'clojure.string))))))

(deftest security-off-test
  (let [sb (sandbox secure-tester :jvm false)]
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
  (let [sb (sandbox #{#'clojure.core/+} :init '(def + 3))]
    (is (thrown-with-msg? SecurityException #"You tripped the alarm!"
          (sb '(clojure.core/+ 3 3))))
    (is (= 3 (sb '+)))))

(deftest block-maps
  (let [sb (sandbox secure-tester)]
    (is (thrown? SecurityException (sb '{:foo (eval '(+ 3 3))})))))

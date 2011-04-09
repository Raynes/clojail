(ns clojail.core-test
  (:use [clojail core testers]
        clojure.test))

(def sb (sandbox secure-tester))
(def easy (sandbox #{}))

(def wbsb (sandbox {:whitelist #{java.io.File java.lang.Math 'new 'clojure.core '+ '-}
                    :blacklist #{'+ java.lang.Math}}))

(deftest dot-test
  (is (= 4 (easy '(. "dots" (length))))))

(deftest dot-shorthand-test
  (is (= true (easy '(= (.length "string") 6)))))

(deftest security-test
  (is (= 7 (sb '(-> "bar.txt" java.io.File. .getName .length))))
  (is (thrown? Exception (sb '(-> java.io.File .getMethods (aget 0) .getName))))
  (is (thrown? Exception (sb '(-> java.io.File .getMethods (aget 0) ((memfn getName)))))))

(deftest sandbox-config-test
  (is (string? (easy '(-> java.io.File .getMethods (aget 0) .getName)))))

(deftest whitelist-test
  (is (= 6 (wbsb '(- 12 6))))
  (is (thrown? Exception (wbsb '(+ 3 3))))
  (is (= (java.io.File. "") (wbsb '(java.io.File. ""))))
  (is (thrown? Exception (wbsb '(java.lang.Math/abs 10)))))

(deftest lazy-dot-test
  (is (= [0 0] (sb '(map #(.length %) ["" ""])))))

(deftest binding-test
  (is (= 2 (sb '(#'inc 2) {#'inc identity}))))

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
    (is (= 10 (dyn-sb #{} code)))
    (is (thrown? SecurityException (dyn-sb '#{+} code)))))

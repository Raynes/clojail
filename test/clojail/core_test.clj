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
  (is (thrown? Exception (sb '(-> java.io.File .getMethods (aget 0) .getName)))))

(deftest sandbox-config-test
  (is (string? (easy '(-> java.io.File .getMethods (aget 0) .getName)))))

(deftest whitelist-test
  (is (= 6 (wbsb '(- 12 6))))
  (is (thrown? Exception (wbsb '(+ 3 3))))
  (is (= (java.io.File. "") (wbsb '(java.io.File. ""))))
  (is (thrown? Exception (wbsb '(java.lang.Math/abs 10)))))
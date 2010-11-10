(ns clojail.core-test
  (:use [clojail core testers]
        clojure.test))

(def sb (sandbox secure-tester))

(deftest dot-test
  (is (= true (sb '(= (.length "string") 6)))))
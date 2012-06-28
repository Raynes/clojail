(defproject clojail "0.6.0"
  :description "A sandboxing library."
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [bultitude "0.1.6"]
                 [serializable-fn "1.1.3"]]
  :aliases {"test-all" ["with-profile" "dev,1.3:dev" "test"]}
  :profiles {:1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}}
  :jvm-opts ["-Djava.security.policy=example.policy"])
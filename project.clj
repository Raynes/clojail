(defproject clojail "0.5.1"
  :description "A sandboxing library."
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :aliases {"test-all" ["with-profile" "dev,1.2:dev,1.3:dev" "test"]}
  :profiles {:1.2 {:dependencies [[org.clojure/clojure "1.2.1"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}}
  :jvm-opts ["-Djava.security.policy=example.policy"])
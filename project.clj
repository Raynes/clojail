(defproject clojail "1.0.3"
  :description "A sandboxing library."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/flatland/clojail"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [bultitude "0.1.6"]
                 [serializable-fn "1.1.3"]]
  :aliases {"testall" ["with-profile" "dev,1.3:dev" "test"]}
  :profiles {:1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}}
  :jvm-opts ["-Djava.security.policy=example.policy"])
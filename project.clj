(defproject clojail "1.0.7"
  :description "A sandboxing library."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/flatland/clojail"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [bultitude "0.2.7"]
                 [serializable-fn "1.1.4"]
                 [org.flatland/useful "0.11.3"]]
  :aliases {"testall" ["with-profile" "dev,1.5:dev" "test"]}
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.0"]]}}
  :jvm-opts ["-Djava.security.policy=example.policy"])

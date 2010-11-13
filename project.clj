(defproject clojail "0.2.0-SNAPSHOT"
  :description "An experimental sandboxing library."
  :dependencies [[clojure "1.2.0"]
                 [clojure-contrib "1.2.0"]]
  :dev-dependencies [[swank-clojure "1.2.1"]
                     [cake-autodoc "0.0.1-SNAPSHOT"]]
  :tasks [cake-autodoc.tasks])

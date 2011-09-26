(defproject clojail "0.4.0-SNAPSHOT"
  :description "An experimental sandboxing library."
  :dependencies [[clojure "1.2.0"]
                 [amalloy/utils "[0.3.7,)"]]
  :dev-dependencies [[swank-clojure "1.2.1"]
                     [clojure-contrib "1.2.0"]
                     [cake-autodoc "0.0.1-SNAPSHOT"]]
  :tasks [cake-autodoc.tasks]
  :main clojail.main)

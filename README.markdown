## Introduction

Welcome to the wonderful world of clojail! Clojail is a library for sandboxing Clojure code. Why is this useful? Well, for tons of reasons. I've personally used a sandbox for two different projects, such as [Try Clojure](http://try-clojure.org) and [sexpbot](http://github.com/Raynes/sexpbot). I've seen a wiki-like website where the pages were Clojure code evaluated in a sandbox.

One of the primary reasons for writing clojail is to replace clj-sandbox in my own personal projects. I'm also trying my best to make sure that it's useful for other people and their unpredictable needs as well. If you have any questions, ideas, or feedback, shoot me a message here on Github or in an email. Feedback of all kind is welcome!

This project is very new. The sandbox *appears* to work properly, but that does not mean it will. The more people that use and test this project, the faster it'll be totally secure. Note that I *will* be using this library in my own projects very soon, and if I find any security flaws or any are reported to me, they will be fixed as fast as possible. If you find what you think is a bug, create an issue here on Github.

## Usage

You can get this library from clojars via [cake](http://github.com/ninjudd/cake) or [leiningen](http://github.com/technomancy/leiningen). The instructions are the same.

First, add `[clojail "0.1.0-SNAPSHOT"]` to your :dependencies in project.clj. After than, just run `cake deps` or `lein deps` if you use Leiningen.

Once you've got that set up, you can play with the sandbox. Let's create a sandbox:

    (ns my.project
      (:use clojail.core)) ; Pull in the library.
    
    (def tester #{'alter-var-root 'java.lang.Thread}) ; Create a blacklist.
    (def sb (sandbox tester :timeout 5000))

You have just created a new sandbox. This sandbox will trigger whenever java.lang.Thread or alter-var-root is used in code. Anything else will pass the sandbox and be executed. Simple enough, right? Also, the :timeout bit is an optional argument. We've just lowered the timeout time to 5000 milliseconds, or 5 seconds. The default is 10000.

This sandbox isn't very secure. Let's create a new sandbox using the secure-tester in clojail.testers. This sandbox should be more secure. Can't promise total security however, since I can't test everything.

    (ns my.project
      (:use [clojail core testers]))
    
    ; Don't bother with changing :timeout unless you want to. It was purely for demonstrational purposes.
    (def sb (sandbox secure-tester))

Alright, cool. Now we have a supposedly secure sandbox. How do we use it? Simple! `sb` is now bound to a function that takes code and executes it in the sandbox.

    (sb '(+ 3 3)) ; Returns 6
    (sb '(def x)) ; Fails because def is not allowed in our sandbox.

Play around with it a bit.

    (sb '(println "blah")) ; Returns nil

Wait... nil? It's doing the right thing and printing output to *out* and returning nil. However, for our purposes, maybe we want to get the output of sb as a string. Luckily, the sandbox accounts for that possibility and allows for you to provide, after the code to execute, a hashmap of vars to values. Here is how we can use that to get the output of sb as a string:

    (let [writer (java.io.StringWriter.)] 
      (sb '(println "blah") {#'*out* writer}) (str writer)) ; Returns "blah\n"

There we go! Great!

Well, that's about all folks. I hope my library is to your liking, and I hope it's useful in your own projects. Remember to not hesitate to give me feedback!

## Things I don't yet have that you might want

Clojail doesn't yet allow for you to whitelist things. I'm sure that whitelisting is useful in the long term, but blacklisting is much more useful in general, so that's what I took priority in. I do plan to offer some sort of whitelisting.

## License

Clojail is licensed under the same thing that Clojure is. See LICENSE.

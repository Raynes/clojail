## Introduction

[![Build Status](https://secure.travis-ci.org/flatland/clojail.png)](http://travis-ci.org/flatland/clojail)

Welcome to the wonderful world of clojail! Clojail is a library for
sandboxing Clojure code. Why is this useful? Well, for tons of
reasons. Clojurians have used this library for a number of different things.
Personally, I've used the library in two of my own projects: 
[Try Clojure](http://try-clojure.org) 
and [lazybot](http://github.com/flatland/lazybot).

If you have any questions, ideas, or feedback, shoot me
a message here on Github or in an email. Feedback of all kind is
welcome!

## Usage

*WARNING*
As of the 0.6.0 release, clojail is no longer compatible with Clojure 1.2.1.
You should absolutely be using the latest version of clojail though so please
do not stick with old versions. It's time to upgrade boys and girls.

You can get this library from clojars via Leiningen.

First, go to [clojars](http://clojars.org/clojail) and see what the latest version is.
Next, add it to your project.clj. After that, just run `lein deps`.

Because clojail employs the JVM's built in sandboxing, you'll need to
have a `~/.java.policy` file to define permissions for your own
code. If you don't do this, you'll get security exceptions. I've
included a very liberal `example.policy` file that you can just copy
over to `~/.java.policy`.

Once you've got that set up, you can play with the sandbox. Let's
create a sandbox:

```clojure
(ns my.project
  (:use [clojail.core :only [sandbox]]
        [clojail.testers :only [blacklist-symbols blacklist-objects]]))
    
(def tester [(blacklist-symbols #{'alter-var-root})
             (blacklist-objects [java.lang.Thread])])) ; Create a blacklist.
(def sb (sandbox tester :timeout 5000))
```

You have just created a new sandbox. This sandbox will trigger
whenever java.lang.Thread or alter-var-root is used in code. Anything
else will pass the sandbox and be executed. Simple enough, right?
Also, the :timeout bit is an optional argument. We've just lowered the
timeout time to 5000 milliseconds, or 5 seconds. The default is 10000.

This sandbox isn't very secure. Let's create a new sandbox using the
secure-tester in clojail.testers. This sandbox should be more
secure. Can't promise total security however, since I can't test
everything.

```clojure
(def sb (sandbox secure-tester))
```

The `secure-tester` tester is a collection of things we have accumulated as
unsafe over time. It blacklists various namespaces, classes, functions, etc.

Don't bother setting `:timeout` unless you just want to. The sandbox
has a reasonable default.

Alright, cool. Now we have a supposedly secure sandbox. How do we use
it? Simple! `sb` is now bound to a function that takes code and
executes it in the sandbox.

```clojure
(sb '(+ 3 3)) ; Returns 6
(sb '(def x)) ; Fails because def is not allowed in our sandbox.
```

Play around with it a bit.

```clojure
(sb '(println "blah")) ; Returns nil
```

Wait... nil? It's doing the right thing and printing output to *out*
and returning nil. However, for our purposes, maybe we want to get the
output of sb as a string. Luckily, the sandbox accounts for that
possibility and allows for you to provide, after the code to execute,
a hashmap of vars to values. Here is how we can use that to get the
output of sb as a string:

```clojure
(let [writer (java.io.StringWriter.)] 
  (sb '(println "blah") {#'*out* writer}) (str writer)) ; Returns "blah\n"
```

There we go! Great! This only works for vars that are explicitly dynamic, just like
normal `binding`.

Well, that's about all folks. I hope our library is to your liking, and
I hope it's useful in your own projects. Remember to not hesitate to
give us feedback! We especially like to hear how people are using sandboxing.

### Testers

If you're a curious fellow or gal, you're probably wondering what exactly a
tester is. Well, in older releases testers were a set of objects. Over time we
realized we could do better. These days, clojail testers are very clever
collections of serializable functions created with the
[serializable-fn](https://github.com/technomancy/serializable-fn) project. These
functions take a 'thing' which is anything clojail pulls out of possibly unsafe
code, so classes, namespaces, symbols, numbers, television, whatever, and they
return a truthy value if the thing is bad or a non truthy value if it passes.

The important part is that they are serializable. They have to be serializable
to a string in order for us to secure java interop. We do some magic that
requires printing the tester to a string (not with print-dup, though that may
seem weird) and then reading it inside of your own code. Because of that,
anything inside of a tester needs to be round-trippable, and that's why we use
serializable functions. We also have a ClojailWrapper type that you can wrap
objects in for your own serializable functions in order to define a print-method
for them, that way you don't have to create a print-method for some top-level
thing in your own code (which is bad because it is global). It is also necessary
for some other things that you probably don't care about.

If none of this makes sense, just take a look at `clojail.testers`. It has, of
course, `secure-tester`, but more importantly it has a bunch of high-level
functions for creating serializable fns for testers out of various objects. We
used some of them above! It is unlikely that you'll have to manually create a
serializable function for usage with clojail, but if you do, this namespace
should be helpful.

## Warning

We can't promise that clojail will be entirely secure at any given
time. Clojail uses a blacklist-based sandbox by default, so there will
almost always be little holes in the sandbox. Fortunately, that only
applies to the Clojure side of things. Since clojail is backed up by
the JVM sandbox, even if the Clojure sandbox is broken, I/O still
can't be done. Even if clojail breaks, the breaker can't wipe your
system unless he has broken the JVM sandbox, in which case he has worked
hard and earned his prize.

## License

Clojail is licensed under the same thing that Clojure is. See LICENSE.

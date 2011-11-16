## Introduction

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

You can get this library from clojars via Leiningen.

First, add `[clojail "0.5.0"]` to your
project.clj. After than, just run `lein deps`.

Because clojail employs the JVM's built in sandboxing, you'll need to
have a `~/.java.policy` file to define permissions for your own
code. If you don't do this, you'll get security exceptions. I've
included a very liberal `example.policy` file that you can just copy
over to `~/.java.policy`.

Once you've got that set up, you can play with the sandbox. Let's
create a sandbox:

```clojure
(ns my.project
  (:use clojail.core)) ; Pull in the library.
    
(def tester #{'alter-var-root java.lang.Thread}) ; Create a blacklist.
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
(ns my.project
  (:use [clojail core testers]))
  
(def sb (sandbox secure-tester))
```

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

A tester is a set of objects, usually symbols, packages, and classes, that
is considered as a blacklist and used to test if code is bad.

A nice feature of clojail is that you can blacklist
entire Java packages. Don't want anything in the java.lang.reflect
package? Fine:

```clojure
(use '[clojail.testers :only [p]])
(def reflect-blacklist #{(p "java.lang.reflect")})
```

Now you have a tester that will scream rape if someone tries to
execute code using any classes from the reflect package.

## Warning

We can't promise that clojail will be entirely secure at any given
time. Clojail uses a blacklist-based sandbox by default, so there will
almost always be little holes in the sandbox. Fortunately, that only
applies to the Clojure side of things. Since clojail is backed up by
the JVM sandbox, even if the Clojure sandbox is broken, I/O still
can't be done. Even if clojail breaks, the breaker can't wipe your
system unless he has broken the JVM sandbox, in which case he has worked
hard and earned his prize.

### What can happen?

If somebody finds a hole in your Clojure sandbox, all they can do is
break the state of the sandbox. Meaning, if they find a way to use
'eval', they can eval any code they like. That code will still be
evaluated under the JVM sandbox. They can, however, use eval to call
def and redefine stuff in the sandbox. This also means they can cause
out-of-memory errors by defining a bunch of stuff. You'll want to
prepare for such things.

We're considering making an effort to maintain a tester in
clojail.testers that tries to block out *everything* that's bad. The
reason we haven't undertaken that task so far is because we don't want
anybody to assume that the tester is totally secure, because you can
never really be certain. `secure-tester` is unfortunately named. It
should be `secure-enough-tester` or `rather-secure-tester`. For the
most part, secure-tester *is* secure enough. Just be aware that there
will probably be holes, just not catastrophic ones.

## License

Clojail is licensed under the same thing that Clojure is. See LICENSE.

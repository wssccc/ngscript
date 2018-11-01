#ngscript: An overview

[![Build Status](https://travis-ci.org/wssccc/ngscript.svg?branch=master)](https://travis-ci.org/wssccc/ngscript)

##Introduction
ngscript is an embedded script language for Java. It has almost the same features as Javascript, in addition, ngscript provides an elegant way to interact with native Java classes and objects. 
Examples in this text are ready to run on http://shell.ngscript.org/ (or http://ngscript.sinaapp.com for faster access), except some related to IO operation.
The "VM" mentioned below, if no special emphasis, is ngscript's WscVM.

##Language elements

###Variable
To define a variable, use `var` statement. 
**Please notice that if you don't initialize the variable once it is declared, ngscript doesn't guarantee the content in it.**

>**Here are some examples**
>
>Define a variable named as var_name
>
>       var var_name;
>
>Inline definition
>
>       for (var i = 0; i < 9; ++i) ...

Variables in ngscript seems typeless, in fact, they are all stored as Object in the VM. 
Primitive types are auto-boxed, but if you call a native method that requires primitive types, the VM unbox primitives automatically.

###Function
####Named function
Named function is declared like

        function func1 (param1, param2) {
            println(param1 + "," + param2);
        }
        
You might as well notice that **named functions are registered in global scope**.

####Lambda
ngscript supports anonymous function, the underlying implements of named function is a variable that stores an anonymous function along with global environment.

>Invoking lambda
>
>       (function (){
>           println("hello");
>       })();
>
>Use a variable to store lambda
>
>       var f = function(){
>           println("hello, stored lambda");
>       };
>

####Native closure
It's known to all that object is a combination of `DATA` and `PROCESS`. The central concept of OOP is the `DATA` stored in members and the `PROCESS` defined as methods.

The other way around, if `DATA` stores in environment(or enclosure variable), `PROCESS` is just a single function, obviously, the combination of `DATA` and `PROCESS` is called function closure.

So ngscript provides a different way to reference native objects, that is what I called "Native closure".

>When we're making reference to native Java's object method, the VM creates a native closure to store it.
>
>       //native Java ArrayList
>       var array = new ArrayList();
>       //add something
>       array.add(1); array.add(2); array.add(3);
>       //reference to method of a native object
>       var ref_get = array.get;
>       //call the reference to get the element of index 1
>       println(ref_get(1));

*ngscript's native closure also supports function overriding*

###Object
ngscript's object system is based on environment and closure, and without annoying things like prototype and dynamic scoping.

####ngscript object
>Define a constructor is like define a named function
>
>       function One(name) {
>           this.hiho = function(){
>               println("I'm " + name);
>           };
>       }
>
>Create an object with `new` operator
>
>       var newone = new One("wssccc");
>       newone.hiho();
>
>Or you can use the empty constructor 
>
>       var otherone = new Object();
>       otherone.hiho = newone.hiho;
>       otherone.hiho();

####Native Java support
Native Java Classes are also available.
>Create an instance of ArrayList
>
>       var arraylist = new ArrayList();
>       arraylist.add(1);
>       arraylist.add(2);
>       println(arraylist.toString());
>       arraylist.remove(0);
>       println(arraylist.toString());
>

####Import Java class
java.lang.\* and java.util.\* are imported by default.
ngscript supports `import` statement, but it's not fully tested yet.

###typeof
`typeof` is an operator to retrieve the type information of data. The return value is a string.
>
>       println(typeof println);
>       var a = 1;
>       println(typeof a);
>       println(typeof println);

###println
println is to print a line.

###eval
eval takes one string parameter, the string can be a valid expression or statements. 
eval executes the code, and return the %eax once the VM returned.
>
>       println(eval("15+20"));
>

###Error handling
ngscript supports traditional try...catch statements to handle exceptions. These exceptions can be both ngscript's exception object(inner class name VMException) and Java native excetpion(inherited from Exception class).

>The try...catch statement is like this
>
>       try {
>               println("will throw excepton");
>               throw "exception";
>       } catch (e) {
>               println(e.toString());
>       }

`throw` statement throws an object as exception body, the object can be anything(integer, string, or something else).
Catchable Java exceptions can be caught as well, but exceptions such as RuntimeException will not caught by the statement.

The current implementation of the VM catches all unhandled exceptions by default. But it may be useful to throw some of the exceptions to the outer host environment. You can modify the VM by yourself to accomplish this.

###Coroutine
Coroutine is an useful feature in certain situation. ngscript supports coroutine by providing a class Coroutine.
>
>       //declare a function first
>       function f(a,b) {
>               println("first = " + a);
>               //call yield to switch to previous
>               //and you can passing a retval
>               yield(1); 
>               //when resume at second time
>               //this coroutine will begins at here
>               println("second = " + b);
>       }
>
>       //create coroutine
>       var co = new Coroutine(f, "p1", "p2"); 
>
>       println("coroutine status=" + co.status());
>       //call resume to switch to coroutine
>       //and will returned with a retval
>       println("resume 1 = " + co.resume());
>       println("coroutine status=" + co.status());
>       //the second resume has nothing returned but garbage
>       println("resume 2 = " + co.resume());
>       println("coroutine status=" + co.status());
>       //when trying to resume a coroutine which is already returned
>       //will throw an exception
>       try {
>               println("resume 3:");
>               println(co.resume());
>       } catch(e) {
>               println("you cannot resume a returned coroutine!");
>       }
>

##other
###ngscript online
[http://shell.ngscript.org/](http://shell.ngscript.org/) is an online version of ngscript.
It is a **REPL** shell, just write your code line, and tap enter to submit.
If you write a valid statement(include expression, while, for, if, etc.), the VM knows it's time to compile and run, then you can see the outputs. Once the compiled code is run to the end, the VM prints the %eax register value.
**If you wrote an incomplete structure and submitted, the prompt will display as `...`, to tell you continue writing. Only complete syntax structure will trigger a compile-run action.**
It's useful to submit a `;` (semicolon) to flush the stream, and clear error status.

Tap tab to active completion, the completion feature covers useful commands, and members in current scope.

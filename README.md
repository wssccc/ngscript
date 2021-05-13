# ngscript

[![Build Status](https://travis-ci.org/wssccc/ngscript.svg?branch=master)](https://travis-ci.org/wssccc/ngscript)

## Introduction
A Javascript-like scripting language for JVM, with coroutine.

## Quick Start
Clone project

`git clone https://github.com/wssccc/ngscript.git`

Run Rose-Render test

 `mvn test -Dtest=org.ngscript.TestRoseRenderer`

## Language References

### Variable
Use `var` to explicitly define a variable 

>**Examples**
>
>Define a variable
>
>       var b;
>
>Define a variable and initialize with a value
>
>       var a = 1;
>
>Inline definition
>
>       for (var i = 0; i < 9; ++i) ...


### Function
#### Named Function
>**Examples**
>
>        function func1 (param1, param2) {
>            println(param1 + "," + param2);
>        }
        
**Named functions were registered in global scope**.

#### Lambda
>**Examples**
>
>
>       (function (){
>           println("hello");
>       })();
>

>       var f = function(){
>           println("hello");
>       };
>

#### Java Method Reference

>**Examples**
>
>       //native Java ArrayList
>       var array = new ArrayList();
>       //add something
>       array.add(1); array.add(2); array.add(3);
>       //reference to method of a Java object
>       var ref_get = array.get;
>       //call the reference to get the element of index 1
>       println(ref_get(1));

### Object

#### ngscript object
>**Examples**
>
>Define a constructor function
>
>       function One(name) {
>           this.hello = function(){
>               println("I'm " + name);
>           };
>       }
>
>Create an object with the `new` operator
>
>       var newone = new One("wssccc");
>       newone.hello();
>

#### Java Object
>**Examples**
>
>Create an ArrayList
>
>       var arraylist = new ArrayList();
>       arraylist.add(1);
>       arraylist.add(2);
>       println(arraylist.toString());
>       arraylist.remove(0);
>       println(arraylist.toString());
>

#### Import
The same as `import` statement in Java

**java.lang.\* and java.util.\* classes were imported by default.**

### typeof
Get string representation of the type of an object
>**Examples**
>
>       println(typeof println);
>       var a = 1;
>       println(typeof a);
>       println(typeof println);

### println
Print a line.

### eval
>
>       println(eval("15+20"));
>

### Error Handling
>**Examples**
>
>       try {
>               println("will throw excepton");
>               throw "exception";
>       } catch (e) {
>               println(e.toString());
>       }

### Coroutine
>**Examples**
>
>       function f(a,b) {
>               println("first = " + a);
>               // return 1 and switch to caller
>               yield(1); 
>               // resume here
>               println("second = " + b);
>       }
>
>       // create coroutine
>       var co = new Coroutine(f, "p1", "p2"); 
>
>       println("coroutine status=" + co.status());
>       // call resume() to run
>       println("resume 1 = " + co.resume());
>       println("coroutine status=" + co.status());
>       // call resume() to run to the end of function f
>       println("resume 2 = " + co.resume());
>       println("coroutine status=" + co.status());
>
>       try {
>               println("resume 3:");
>               println(co.resume());
>       } catch(e) {
>               println("could not resume, status=" + co.status());
>       }
>

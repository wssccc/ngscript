# ngscript

[![Build Status](https://travis-ci.org/wssccc/ngscript.svg?branch=master)](https://travis-ci.org/wssccc/ngscript)

## Quick Guide
Clone the project `git clone https://github.com/wssccc/ngscript.git`

Use `mvn test -DfailIfNoTests=false -Dtest=org.ngscript.TestRoseRenderer` to run the rose renderer test.

An interactive online demo is available at https://shell.ngscript.org/.
``
## Introduction
ngscript is an embedded script language for Java. It's a javascript-like language, with some impressive improvements, such as coroutine and tail call optimization(experimental).


## Language References

### Variable
To define a variable, use `var` statement. 
**Uninitialized variable may contain garbage.**

>**Examples**
>
>Define a variable
>
>       var b;
>
>Define a variable and initialize
>
>       var a = 1;
>
>Inline definition
>
>       for (var i = 0; i < 9; ++i) ...

Variables are dynamic-typed objects, primitive-typed values(int, long, double ... ) are auto-boxed.

### Function
#### Named Function
>**Examples**
>
>        function func1 (param1, param2) {
>            println(param1 + "," + param2);
>        }
        
**Named functions are registered in global scope**.

#### Lambda
>**Examples**
>
>Defining and invoking lambda
>
>       (function (){
>           println("hello");
>       })();
>
>Lambda is a first-class object, which can be assigned to a variable
>
>       var f = function(){
>           println("hello");
>       };
>

#### Reference To Java Method
ngscript supports a way to make reference to a method of a Java object. The reference, like lambda, is a first-class object.

>**Examples**
>
>Making reference of a Java object
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
>Define a constructor
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
>Use the empty constructor 
>
>       var otherone = new Object();
>       otherone.hiho = newone.hiho;
>       otherone.hiho();
**Dynamic scoping and prototype are not supported**

#### Java Object
>**Examples**
>
>Create an instance of ArrayList
>
>       var arraylist = new ArrayList();
>       arraylist.add(1);
>       arraylist.add(2);
>       println(arraylist.toString());
>       arraylist.remove(0);
>       println(arraylist.toString());
>

#### Import Java Class
Use `import` statement

**java.lang.\* and java.util.\* are imported by default.**

### typeof
Get a string representation of the type of a given object
>**Examples**
>
>       println(typeof println);
>       var a = 1;
>       println(typeof a);
>       println(typeof println);

### println
println is to print a line.

### eval
Execute a small piece of script and retrieve the result.
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

`throw` statement throws an object as exception body, the object can be anything(integer, string, or something else).

### Coroutine
>**Examples**
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

## Other
### ngscript online
https://shell.ngscript.org/ is a website for trying ngscript online.
It's a **REPL** shell, with tab-completion support.
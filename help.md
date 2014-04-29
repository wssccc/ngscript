#ngscript: An overview

##Introduction
ngscript is an embeded script language for Java. It provides almost the same features as Javascript, and in addtion, ngscript provide an elegant way to interact with native Java classes and objects. 
Examples in this text are ready to run on http://ngscript.sinaapp.com/ , except some related to IO operation.
The "VM" mentioned below, if no special emphasis, is ngscript's WscVM.

##Language elements

###Variable
To define a variable, use `var` statement. 

>**Here is some examples**
>
>Define a variable named as var_name
>
>       var var_name;
>
>Inline definition
>
>       for (var i = 0; i < 9; ++i) ...

Variables in ngscript seems typeless, but in fact they are all stored as Object in the VM. 
Primitive types are auto-boxed, but if you call a native method that requires primitive types, the VM unbox primitives automatically.

###Function
####Named function
Named function is declared like

        function func1 (param1, param2) {
            println(param1 + "," + param2);
        }
        
You might as well notice that **named functions are registered in global scope**.

####Lambda
ngscript supports anonymous function, in fact, the underlying implements of named function is a variable that stores a anonymous function along with global environment.

>Instant call of lambda
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

*If you're looking for more creative usage of lambda, read the SICP wizard book*

####Native closure
It's known to all that object is a combination of `DATA` and `PROCESS`. The centeral concept of OOP is the `DATA` stored in members and the `PROCESS` defined as methods. 

The other way around, if `DATA` stores in environment(or enclosure variable), `PROCESS` is just a single function, obviously, the combination of `DATA` and `PROCESS` is our function closure.

So ngscript provides a different way to present native objects, that is what I called "Native closure".

>When we're making references of native Java's object method, the VM creates a native closure object to present that.
>
>       var array = new ArrayList();                //native Java ArrayList
>       array.add(1); array.add(2); array.add(3);   //add something
>       var ref_get = array.get;                    //make a reference to method of a native object
>       println(ref_get(1));                        //call the reference to get the element of index 1

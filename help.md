#ngscript: An overview

##Introduction
ngscript is an embeded script language for Java. It provides almost the same features as Javascript, and in addtion, ngscript provide an elegant way to interact with native Java classes and objects. 
Examples in this text are ready to run on http://ngscript.sinaapp.com/ , except some related to IO operation.

##Language elements

###Variable
To define a variable, use `var` statement. 

>**Here is some examples**
>
>Define a variable named as var_name
>
>     var var_name;
>
>Inline definition
>
>     for (var i = 0; i < 9; ++i) ...

Variables in ngscript seems typeless, but in fact they are all stored as Object in the VM. 
Primitive types 

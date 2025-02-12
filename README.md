# ngscript

[![CircleCI](https://circleci.com/gh/wssccc/ngscript/tree/master.svg?style=svg)](https://circleci.com/gh/wssccc/ngscript/tree/master)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=wssccc_ngscript&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=wssccc_ngscript)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=wssccc_ngscript&metric=sqale_index)](https://sonarcloud.io/dashboard?id=wssccc_ngscript)

## Overview

ngscript is a modern scripting language designed for the Java Virtual Machine (JVM) that combines JavaScript-like syntax with powerful coroutine support. It provides a familiar programming experience while leveraging the robust features of the JVM ecosystem.

## Getting Started

### Prerequisites

- Java Development Kit (JDK)
- Maven

### Installation

Clone the repository:

```bash
git clone https://github.com/wssccc/ngscript.git
```

### Running Tests

Execute the conformance test suite:

```bash
mvn test -Dtest=org.ngscript.ConformanceTest -Dsurefire.failIfNoSpecifiedTests=false
```

### Running Examples

Launch the Rose-Render example:

```bash
mvn exec:java -Dexec.mainClass=org.ngscript.examples.RoseRender
```

## Language Reference

### Variables

Variables in ngscript are explicitly declared using the `var` keyword.

>**Examples**
>
>Define a variable
>
>       var counter;
>
>Define a variable and initialize with a value
>
>       var total = 1;
>
>Inline definition
>
>       for (var index = 0; index < 9; ++index) ...

### Functions

#### Named Functions

Named functions are registered in the global scope and can be called from anywhere in the program.

>**Examples**
>
>        function calculateSum(firstValue, secondValue) {
>            println(firstValue + "," + secondValue);
>        }

**Named functions were registered in global scope**.

#### Lambda Expressions

ngscript supports both traditional function expressions and arrow function syntax for concise lambda definitions.

>**Examples**
>
>
>       (function (){
>           println("Hello, World!");
>       })();
>

>       var greetingFunction = function(){
>           println("Hello, World!");
>       };
>

>       val add = (x, y) => { x + y; };
>
>       val increment = (value) => {
>           return value + 1;
>       };
>
>       val sum = (x, y) => {
>           return x + y;
>       };
>
>       println("Calling an arrow function");
>       println(increment(1));

#### Java Method References

ngscript allows direct reference to Java methods, enabling seamless integration with Java libraries.

>**Examples**
>
>       // Create a native Java ArrayList
>       var numberList = new ArrayList();
>       // Add elements to the list
>       numberList.add(1); numberList.add(2); numberList.add(3);
>       // Reference to method of a Java object
>       var getElement = numberList.get;
>       // Call the reference to get the element at index 1
>       println(getElement(1));

### Concurrency

#### Go Statements

The `go` statement enables concurrent execution of functions.

>**Examples**
>
>       val concurrentTask = function(taskId) {
>           println("Executing in concurrent routine " + taskId);
>       };
>
>       go concurrentTask(123);
>       println("Executing in main routine");
>

### Object-Oriented Programming

#### ngscript Objects

ngscript supports object-oriented programming with constructor functions and the `new` operator.

>**Examples**
>
>Define a constructor function
>
>       function Person(name) {
>           this.greet = function(){
>               println("I'm " + name);
>           };
>       }
>
>Create an object with the `new` operator
>
>       var person = new Person("John");
>       person.greet();
>

#### Java Object Integration

Seamless integration with Java objects and collections.

>**Examples**
>
>Create an ArrayList
>
>       var numberList = new ArrayList();
>       numberList.add(1);
>       numberList.add(2);
>       println(numberList.toString());
>       numberList.remove(0);
>       println(numberList.toString());
>

### Module System

#### Import Statements

Similar to Java's import system, with automatic imports for common packages.

**java.lang.\* and java.util.\* classes were imported by default.**

### Type System

#### Typeof Operator

Retrieve the string representation of an object's type.

>**Examples**
>
>       println(typeof println);
>       var number = 1;
>       println(typeof number);
>       println(typeof println);

### Built-in Functions

#### Println

Output a line to the console.

#### Eval

Evaluate a string as ngscript code.

>
>       println(eval("15+20"));
>

### Error Handling

Comprehensive try-catch mechanism for robust error management.

>**Examples**
>
>       try {
>               println("About to throw an exception");
>               throw "Custom exception message";
>       } catch (error) {
>               println(error.toString());
>       }

### Coroutines

Advanced coroutine support for cooperative multitasking.

>**Examples**
>
>       function processData(firstParam, secondParam) {
>               println("Processing first parameter: " + firstParam);
>               // Return 1 and switch to caller
>               yield(1); 
>               // Resume here
>               println("Processing second parameter: " + secondParam);
>       }
>
>       // Create coroutine
>       var dataProcessor = new Coroutine(processData, "param1", "param2"); 
>
>       println("Coroutine status: " + dataProcessor.status());
>       // Call resume() to run
>       println("Resume result 1: " + dataProcessor.resume());
>       println("Coroutine status: " + dataProcessor.status());
>       // Call resume() to run to the end of function processData
>       println("Resume result 2: " + dataProcessor.resume());
>       println("Coroutine status: " + dataProcessor.status());
>
>       try {
>               println("Attempting third resume:");
>               println(dataProcessor.resume());
>       } catch(error) {
>               println("Could not resume, status: " + dataProcessor.status());
>       }
>

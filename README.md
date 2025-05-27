# ngscript

[![CircleCI](https://circleci.com/gh/wssccc/ngscript/tree/master.svg?style=svg)](https://circleci.com/gh/wssccc/ngscript/tree/master)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=wssccc_ngscript&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=wssccc_ngscript)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=wssccc_ngscript&metric=sqale_index)](https://sonarcloud.io/dashboard?id=wssccc_ngscript)

## Overview

ngscript is a modern scripting language designed for the Java Virtual Machine (JVM) that combines JavaScript-like syntax with powerful coroutine support. It provides a familiar programming experience while leveraging the robust features of the JVM ecosystem.

## Quick Start

### Prerequisites

- Java Development Kit (JDK)
- Maven

### Installation

Clone the repository:

```bash
git clone https://github.com/wssccc/ngscript.git
```

### First Example

Here's a simple Hello World example:

```javascript
println("Hello, World!");
```

## Language Features

### Basic Syntax

#### Variables

Variables in ngscript are explicitly declared using the `var` keyword.

**Examples**

Define a variable:

```javascript
var counter;
```

Define a variable and initialize with a value:

```javascript
var total = 1;
```

Inline definition:

```javascript
for (var index = 0; index < 9; ++index) ...
```

#### Type System

##### Typeof Operator

Retrieve the string representation of an object's type.

**Examples**

```javascript
println(typeof println);
var number = 1;
println(typeof number);
println(typeof println);
```

#### Built-in Functions

##### Println

Output a line to the console.

##### Eval

Evaluate a string as ngscript code.

```javascript
println(eval("15+20"));
```

### Functions & Modules

#### Named Functions

Named functions are registered in the global scope and can be called from anywhere in the program.

**Examples**

```javascript
function calculateSum(firstValue, secondValue) {
    println(firstValue + "," + secondValue);
}
```

**Named functions were registered in global scope**.

#### Lambda Expressions

ngscript supports both traditional function expressions and arrow function syntax for concise lambda definitions.

**Examples**

```javascript
(function (){
    println("Hello, World!");
})();
```

```javascript
var greetingFunction = function(){
    println("Hello, World!");
};
```

```javascript
val add = (x, y) => { x + y; };

val increment = (value) => {
    return value + 1;
};

val sum = (x, y) => {
    return x + y;
};

println("Calling an arrow function");
println(increment(1));
```

#### Module System

##### Import Statements

Similar to Java's import system, with automatic imports for common packages.

**java.lang.\* and java.util.\* classes were imported by default.**

### Advanced Features

#### Concurrency

##### Go Statements

The `go` statement enables concurrent execution of functions.

**Examples**

```javascript
val concurrentTask = function(taskId) {
    println("Executing in concurrent routine " + taskId);
};

go concurrentTask(123);
println("Executing in main routine");
```

#### Coroutines

Advanced coroutine support for cooperative multitasking.

**Examples**

```javascript
function processData(firstParam, secondParam) {
        println("Processing first parameter: " + firstParam);
        // Return 1 and switch to caller
        yield(1); 
        // Resume here
        println("Processing second parameter: " + secondParam);
}

// Create coroutine
var dataProcessor = new Coroutine(processData, "param1", "param2"); 

println("Coroutine status: " + dataProcessor.status());
// Call resume() to run
println("Resume result 1: " + dataProcessor.resume());
println("Coroutine status: " + dataProcessor.status());
// Call resume() to run to the end of function processData
println("Resume result 2: " + dataProcessor.resume());
println("Coroutine status: " + dataProcessor.status());

try {
        println("Attempting third resume:");
        println(dataProcessor.resume());
} catch(error) {
        println("Could not resume, status: " + dataProcessor.status());
}
```

#### Error Handling

Comprehensive try-catch mechanism for robust error management.

**Examples**

```javascript
try {
        println("About to throw an exception");
        throw "Custom exception message";
} catch (error) {
        println(error.toString());
}
```

### Java Integration

#### Java Object Integration

Seamless integration with Java objects and collections.

**Examples**

Create an ArrayList:

```javascript
var numberList = new ArrayList();
numberList.add(1);
numberList.add(2);
println(numberList.toString());
numberList.remove(0);
println(numberList.toString());
```

#### Java Method References

ngscript allows direct reference to Java methods, enabling seamless integration with Java libraries.

**Examples**

```javascript
// Create a native Java ArrayList
var numberList = new ArrayList();
// Add elements to the list
numberList.add(1); numberList.add(2); numberList.add(3);
// Reference to method of a Java object
var getElement = numberList.get;
// Call the reference to get the element at index 1
println(getElement(1));
```

## Examples & Tutorials

### Running Examples

Launch the Rose-Render example:

```bash
mvn exec:java -Dexec.mainClass=org.ngscript.examples.RoseRender
```

## Development

### Running Tests

Run the conformance test suite to verify ngscript behavior:

```bash
mvn test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=org.ngscript.NgscriptSpecTest
```

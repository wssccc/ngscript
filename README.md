# ngscript

[![Java CI with Maven](https://github.com/wssccc/ngscript/actions/workflows/maven.yml/badge.svg)](https://github.com/wssccc/ngscript/actions/workflows/maven.yml)
[![CodeQL Advanced](https://github.com/wssccc/ngscript/actions/workflows/codeql.yml/badge.svg)](https://github.com/wssccc/ngscript/actions/workflows/codeql.yml)

## Overview

ngscript is a modern scripting language designed for the Java Virtual Machine (JVM) that combines JavaScript-like syntax with powerful coroutine support. It provides a familiar programming experience while leveraging the robust features of the JVM ecosystem.

## Project Structure

ngscript is a multi-module Maven project:

| Module | Description |
|--------|-------------|
| `ngscript-parseroid` | Self-contained LALR parser generator framework |
| `ngscript-core` | Engine core: lexer, parser, compiler, runtime, builtins |
| `ngscript-shell` | Command line tool and interactive REPL |
| `ngscript-examples` | Swing demo applications (built but not published) |

The TC39 [test262](https://github.com/tc39/test262) conformance suite is not vendored in the repository; fetch it on demand with `tools/fetch-test262.sh` (resulting `test262/` directory is git-ignored).

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
mvn exec:java -Dexec.mainClass=org.ngscript.examples.RoseRender -pl ngscript-examples
```

## Development

### Running Tests

Run all tests across modules:

```bash
mvn test
```

## Interactive Shell (REPL)

ngscript provides an interactive shell with tab completion support.

### Starting the REPL

```bash
mvn exec:java -Dexec.mainClass=org.ngscript.shell.Repl -pl ngscript-shell
```

Or after building:

```bash
java -cp ngscript-shell/target/classes:ngscript-shell/target/dependency/* org.ngscript.shell.Repl
```

### Shell Features

- **Tab Completion**: Press `Tab` to complete keywords, variables, and object members
- **Auto Completion**: Completion suggestions appear automatically as you type
- **History**: Use up/down arrows to navigate previous commands
- **Multi-line Input**: The shell automatically handles multi-line statements

### Completion Types

The shell supports the following completion types:

| Type | Description | Example |
|------|-------------|---------|
| Keywords | Language keywords | `fun` → `function` |
| Builtins | Built-in functions and objects | `prin` → `println`, `print` |
| Variables | User-defined variables in scope | `myV` → `myVariable` |
| Object Members | Properties and methods of objects | `obj.` → lists all properties |
| Java Classes | Imported Java classes | `Arr` → `ArrayList`, `Arrays` |

### Example Session

```
ngscript> var myVariable = 42;
ngscript> myV<Tab>
myVariable
ngscript> myVariable
42
ngscript> new ArrayList().<Tab>
add              addAll           clear            contains
forEach          get              indexOf          isEmpty
iterator         listIterator     remove           removeAll
...
ngscript> 
```

### Disabling Completion

If needed, completion can be disabled by setting `completionEnabled` to `false` in the configuration.

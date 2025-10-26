/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.value;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgsObject;

/**
 * @author wssccc
 */
public class ScriptFunction {

    public String functionLabel;
    public NgsObject environment;
    public Machine vm;

    /**
     * The function's name (ES Function.name), null for anonymous functions.
     */
    public String name;

    /**
     * The declared parameter count (ES Function.length).
     */
    public int params;

    /**
     * The prototype object handed to instances created with {@code new}
     * (ES function prototype), created lazily.
     */
    public NgsObject prototype;

    /**
     * The this binding of the caller, saved when this function is invoked and
     * restored by {@code ret} (single-threaded machines only).
     */
    public Object savedThis;

    /**
     * Whether this function is an arrow function: arrows have no own
     * {@code this} and are not constructible. When true, {@link #capturedThis}
     * holds the this binding captured at creation time.
     */
    public boolean arrow;

    /**
     * The lexical this binding captured when an arrow function was created;
     * null for ordinary functions.
     */
    public Object capturedThis;

    /**
     * Whether this is a generator function ({@code function*}): calling it
     * returns a {@link org.ngscript.runtime.Generator} iterator instead of
     * running the body.
     */
    public boolean generator;

    /**
     * Whether this is an async function ({@code async function ...}):
     * calling it returns a Promise; the body runs until the first await
     * suspends it, then resumes from a microtask.
     */
    public boolean async;

    /**
     * Bound-function state ({@code Function.prototype.bind}): the fixed
     * this binding and the leading bound arguments. The bound function has
     * no body of its own; calls delegate to the target (a ScriptFunction or
     * an Invokable).
     */
    public Object boundTarget;
    public Object boundThis;
    public Object[] boundArgs;

    /**
     * True for the Function.prototype placeholder: it has no body, and its
     * call/apply/bind use the runtime this binding as the target function.
     */
    public boolean placeholder;

    /**
     * For placeholder functions: "call", "apply" or "bind" (the generic
     * method they represent).
     */
    public String callMode;

    public ScriptFunction(String functionLabel, NgsObject environment, Machine vm) {
        this.functionLabel = functionLabel;
        this.environment = environment;
        this.vm = vm;
    }

    public NgsObject getPrototypeObject() {
        if (prototype == null) {
            prototype = new NgsObject(null);
            // the constructor's prototype links to Object.prototype
            prototype.setPrototype(vm.objectPrototype);
        }
        return prototype;
    }
}

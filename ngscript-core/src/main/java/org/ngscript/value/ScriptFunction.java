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

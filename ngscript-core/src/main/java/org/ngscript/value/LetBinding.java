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

/**
 * A let/const binding: enforces the temporal dead zone (reading before the
 * declaration is initialised throws a ReferenceError) and rejects
 * re-assignment of const bindings with a TypeError.
 *
 * <p>The thrown exception types map to the ES error builtins:
 * ReferenceError is {@link RuntimeException}, TypeError is
 * {@link IllegalArgumentException}.</p>
 *
 * @author wssccc
 */
public class LetBinding extends MemRef {

    private final boolean isConst;
    private final String name;
    private boolean initialized;

    public LetBinding(String name, boolean isConst) {
        super();
        this.name = name;
        this.isConst = isConst;
        this.initialized = false;
    }

    @Override
    public void write(Object v) {
        if (isConst && initialized) {
            throw new IllegalArgumentException("Assignment to constant variable '" + name + "'");
        }
        super.write(v);
        initialized = true;
    }

    @Override
    public Object read() {
        if (!initialized) {
            throw new RuntimeException("Cannot access '" + name + "' before initialization");
        }
        return super.read();
    }

    /** The binding name (for TDZ error messages). */
    public String getName() {
        return name;
    }

    /** Whether the binding has been initialized (TDZ write checks). */
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public String toString() {
        return String.format("LetBinding[%s, const=%b, initialized=%b]", name, isConst, initialized);
    }
}

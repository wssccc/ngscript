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

package org.ngscript.builtins;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgsArray;
import org.ngscript.runtime.NgsObject;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;

/**
 * The ES {@code Array} constructor: {@code Array(n)} / {@code new Array(n)}
 * creates an array (with length n when a single number is given), and the
 * static methods isArray/of are dispatched from
 * {@link org.ngscript.runtime.OpObject#member_ref}.
 *
 * @author wssccc
 */
public class ArrayCtor implements Invokable {

    /** The shared Array.prototype (for instanceof and prototype access). */
    public NgsObject getPrototype() {
        return Builtins.getArrayPrototype();
    }

    @Override
    public void invoke(Machine vm, Object[] args) {
        NgsArray out = new NgsArray(null);
        out.setPrototype(vm.arrayPrototype);
        if (args.length == 1 && args[0] instanceof Integer) {
            // Array(n) creates a sparse array of length n
            out.set("length", args[0]);
        } else {
            for (int i = 0; i < args.length; i++) {
                out.set("" + i, args[i]);
            }
        }
        vm.eax.write(out);
    }

    public static MemRef lookup(ArrayCtor ctor, String member, Machine vm) {
        switch (member) {
            case "isArray":
                return new MemRef((Invokable) (vm2, args) ->
                        vm2.eax.write(args.length > 0 && args[0] instanceof NgsArray));
            case "of":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsArray out = new NgsArray(null);
                    out.setPrototype(vm2.arrayPrototype);
                    for (int i = 0; i < args.length; i++) {
                        out.set("" + i, args[i]);
                    }
                    vm2.eax.write(out);
                });
            case "from":
                return new MemRef((Invokable) (vm2, args) -> {
                    // Array.from(iterable[, mapFn[, thisArg]])
                    Object iterable = args.length > 0 ? args[0] : null;
                    Object mapFn = args.length > 1 ? args[1] : null;
                    Object thisArg = args.length > 2 ? args[2] : null;
                    NgsArray out = new NgsArray(null);
                    out.setPrototype(vm2.arrayPrototype);
                    if (iterable == null || iterable == org.ngscript.value.Undefined.value) {
                        throw new IllegalArgumentException("Array.from: value is not iterable");
                    }
                    Object itObj;
                    try {
                        itObj = org.ngscript.runtime.OpUtils.getIterator(vm2, iterable);
                    } catch (Exception e) {
                        // non-iterable (e.g. a plain number): fall back to
                        // array-like copying by length, per ES
                        double lenD = org.ngscript.runtime.OpUtils.toNumber(
                                org.ngscript.runtime.OpUtils.getMember(vm2, iterable, "length"));
                        int len = (int) lenD;
                        for (int i = 0; i < len; i++) {
                            Object v = org.ngscript.runtime.OpUtils.getMember(vm2, iterable, "" + i);
                            if (mapFn instanceof org.ngscript.value.Invokable
                                    || mapFn instanceof org.ngscript.value.ScriptFunction) {
                                v = vm2.callFunctionSync(mapFn, new Object[]{v, i}, thisArg);
                            }
                            out.set("" + i, v);
                        }
                        vm2.eax.write(out);
                        return;
                    }
                    java.util.Iterator<Object> it = toJavaIterator(vm2, itObj);
                    int i = 0;
                    while (it.hasNext()) {
                        Object v = it.next();
                        if (mapFn instanceof org.ngscript.value.Invokable
                                || mapFn instanceof org.ngscript.value.ScriptFunction) {
                            v = vm2.callFunctionSync(mapFn, new Object[]{v, i}, thisArg);
                        }
                        out.set("" + i++, v);
                    }
                    vm2.eax.write(out);
                });
            default:
                return null;
        }
    }

    /** Adapts any ES iterator (Java iterator or {next()} protocol object). */
    private static java.util.Iterator<Object> toJavaIterator(final Machine vm, final Object itObj) {
        if (itObj instanceof java.util.Iterator) {
            @SuppressWarnings("unchecked")
            java.util.Iterator<Object> it = (java.util.Iterator<Object>) itObj;
            return it;
        }
        // ES iterator protocol: poll next() and read the result object's
        // done/value properties
        return new java.util.Iterator<Object>() {
            private Object next;
            private boolean done;

            private void advance() {
                if (done) {
                    return;
                }
                try {
                    Object method = org.ngscript.runtime.OpUtils.getMember(vm, itObj, "next");
                    Object result = org.ngscript.runtime.OpUtils.callValue(vm, method, itObj, new Object[0]);
                    if (result instanceof org.ngscript.runtime.NgsObject
                            && !org.ngscript.runtime.OpUtils.testValue(((org.ngscript.runtime.NgsObject) result).get("done"))) {
                        next = ((org.ngscript.runtime.NgsObject) result).get("value");
                    } else {
                        done = true;
                    }
                } catch (Exception ignored) {
                    done = true;
                }
            }

            @Override
            public boolean hasNext() {
                if (!done) {
                    advance();
                }
                return !done;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                return next;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}

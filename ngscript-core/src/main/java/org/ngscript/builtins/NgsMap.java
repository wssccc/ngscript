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
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.NgsArray;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.Undefined;

/**
 * The ES {@code Map} builtin: key/value pairs with SameValueZero key
 * semantics, insertion order iteration, and the standard methods
 * (set/get/has/delete/clear/forEach/keys/values/entries/@@iterator/size).
 *
 * @author wssccc
 */
public class NgsMap extends NgsObject {

    private final java.util.Map<Object, Object> entries = new java.util.LinkedHashMap<>();

    public NgsMap(Machine vm) {
        super((NgsObject) vm.env.read());
        setPrototype(vm.objectPrototype);
    }

    /** Normalizes a key to the ES SameValueZero representation. */
    static Object keyOf(Object key) {
        if (key instanceof Integer) {
            return ((Integer) key).doubleValue();
        }
        if (key instanceof Double) {
            double d = (Double) key;
            return d == 0.0 ? 0.0 : key; // -0 == 0
        }
        return key;
    }

    @Override
    public MemRef lookup(String member, Machine vm, boolean isMember) throws NgScriptRuntimeException {
        switch (member) {
            case "size":
                return new MemRef(entries.size());
            case "set":
                return new MemRef((Invokable) (m, a) -> {
                    entries.put(keyOf(a[0]), a[1]);
                    m.eax.write(m.thisRef.read());
                });
            case "get":
                return new MemRef((Invokable) (m, a) -> {
                    Object v = entries.get(keyOf(a[0]));
                    m.eax.write(v == null ? Undefined.value : v);
                });
            case "has":
                return new MemRef((Invokable) (m, a) ->
                        m.eax.write(entries.containsKey(keyOf(a[0]))));
            case "delete":
                return new MemRef((Invokable) (m, a) -> {
                    Object prev = entries.remove(keyOf(a[0]));
                    m.eax.write(prev != null || entries.containsKey(keyOf(a[0])));
                });
            case "clear":
                return new MemRef((Invokable) (m, a) -> {
                    entries.clear();
                    m.eax.write(Undefined.value);
                });
            case "forEach":
                return new MemRef((Invokable) (m, a) -> {
                    Object cb = a[0];
                    Object thisArg = a.length > 1 ? a[1] : Undefined.value;
                    for (java.util.Map.Entry<Object, Object> e : entries.entrySet()) {
                        OpUtils.callValue(m, cb, thisArg,
                                new Object[]{e.getValue(), e.getKey(), this});
                    }
                    m.eax.write(Undefined.value);
                });
            case "keys":
                return new MemRef((Invokable) (m, a) ->
                        m.eax.write(new java.util.ArrayList<>(entries.keySet()).iterator()));
            case "values":
                return new MemRef((Invokable) (m, a) ->
                        m.eax.write(new java.util.ArrayList<>(entries.values()).iterator()));
            case "entries":
            case "@@iterator":
                return new MemRef((Invokable) (m, a) ->
                        m.eax.write(new java.util.ArrayList<>(entries.entrySet()).iterator()));
            default:
                return super.lookup(member, vm, isMember);
        }
    }

    /** The Map constructor: Map(iterable) copies the pairs. */
    public static class Ctor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) throws Exception {
            NgsMap map = new NgsMap(vm);
            if (args.length > 0 && args[0] != null && args[0] != Undefined.value) {
                Object iterator = OpUtils.getIterator(vm, args[0]);
                while (true) {
                    Object[] pair = new Object[2];
                    boolean done = true;
                    if (iterator instanceof java.util.Iterator) {
                        java.util.Iterator it = (java.util.Iterator) iterator;
                        if (!it.hasNext()) {
                            break;
                        }
                        Object entry = it.next();
                        if (entry instanceof java.util.Map.Entry) {
                            java.util.Map.Entry e = (java.util.Map.Entry) entry;
                            map.entries.put(keyOf(e.getKey()), e.getValue());
                            continue;
                        }
                        if (entry instanceof NgsArray) {
                            NgsArray arr = (NgsArray) entry;
                            map.entries.put(keyOf(arr.get("0")), arr.get("1"));
                            continue;
                        }
                        pair[0] = entry;
                        pair[1] = Undefined.value;
                    } else {
                        Object method = OpUtils.getMember(vm, iterator, "next");
                        Object result = OpUtils.callValue(vm, method, iterator, new Object[0]);
                        if (!(result instanceof NgsObject)
                                || OpUtils.testValue(((NgsObject) result).get("done"))) {
                            break;
                        }
                        Object entry = ((NgsObject) result).get("value");
                        if (entry instanceof NgsArray) {
                            NgsArray arr = (NgsArray) entry;
                            map.entries.put(keyOf(arr.get("0")), arr.get("1"));
                            continue;
                        }
                        pair[0] = entry;
                        pair[1] = Undefined.value;
                    }
                    map.entries.put(keyOf(pair[0]), pair[1]);
                    if (done) {
                        break;
                    }
                }
            }
            vm.eax.write(map);
        }
    }
}

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
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.Undefined;

/**
 * The ES {@code Set} builtin: unique values with SameValueZero semantics,
 * insertion order iteration, and the standard methods
 * (add/has/delete/clear/forEach/keys/values/entries/@@iterator/size).
 *
 * @author wssccc
 */
public class NgsSet extends NgsObject {

    private final java.util.Set<Object> entries = new java.util.LinkedHashSet<>();

    public NgsSet(Machine vm) {
        super((NgsObject) vm.env.read());
        setPrototype(vm.objectPrototype);
    }

    @Override
    public MemRef lookup(String member, Machine vm, boolean isMember) throws NgScriptRuntimeException {
        switch (member) {
            case "size":
                return new MemRef(entries.size());
            case "add":
                return new MemRef((Invokable) (m, a) -> {
                    entries.add(NgsMap.keyOf(a[0]));
                    m.eax.write(m.thisRef.read());
                });
            case "has":
                return new MemRef((Invokable) (m, a) ->
                        m.eax.write(entries.contains(NgsMap.keyOf(a[0]))));
            case "delete":
                return new MemRef((Invokable) (m, a) ->
                        m.eax.write(entries.remove(NgsMap.keyOf(a[0]))));
            case "clear":
                return new MemRef((Invokable) (m, a) -> {
                    entries.clear();
                    m.eax.write(Undefined.value);
                });
            case "forEach":
                return new MemRef((Invokable) (m, a) -> {
                    Object cb = a[0];
                    Object thisArg = a.length > 1 ? a[1] : Undefined.value;
                    for (Object v : entries) {
                        OpUtils.callValue(m, cb, thisArg, new Object[]{v, v, this});
                    }
                    m.eax.write(Undefined.value);
                });
            case "keys":
            case "values":
            case "@@iterator":
                return new MemRef((Invokable) (m, a) ->
                        m.eax.write(new java.util.ArrayList<>(entries).iterator()));
            case "entries":
                return new MemRef((Invokable) (m, a) -> {
                    java.util.List<Object> pairs = new java.util.ArrayList<>();
                    for (Object v : entries) {
                        Object[] pair = new Object[]{v, v};
                        pairs.add(pair);
                    }
                    m.eax.write(pairs.iterator());
                });
            default:
                return super.lookup(member, vm, isMember);
        }
    }

    /** The Set constructor: Set(iterable) copies the values. */
    public static class Ctor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) throws Exception {
            NgsSet set = new NgsSet(vm);
            if (args.length > 0 && args[0] != null && args[0] != Undefined.value) {
                Object iterator = OpUtils.getIterator(vm, args[0]);
                while (true) {
                    Object value;
                    if (iterator instanceof java.util.Iterator) {
                        java.util.Iterator it = (java.util.Iterator) iterator;
                        if (!it.hasNext()) {
                            break;
                        }
                        value = it.next();
                    } else {
                        Object method = OpUtils.getMember(vm, iterator, "next");
                        Object result = OpUtils.callValue(vm, method, iterator, new Object[0]);
                        if (!(result instanceof NgsObject)
                                || OpUtils.testValue(((NgsObject) result).get("done"))) {
                            break;
                        }
                        value = ((NgsObject) result).get("value");
                    }
                    set.entries.add(NgsMap.keyOf(value));
                }
            }
            vm.eax.write(set);
        }
    }
}

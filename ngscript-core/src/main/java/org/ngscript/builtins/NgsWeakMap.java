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
 * The ES {@code WeakMap} builtin: object keys only (weak references, no
 * iteration), with set/get/has/delete. The constructor accepts an iterable
 * of [key, value] pairs.
 *
 * @author wssccc
 */
public class NgsWeakMap extends NgsObject {

    private final java.util.Map<Object, Object> entries = new java.util.WeakHashMap<>();

    public NgsWeakMap(Machine vm) {
        super((NgsObject) vm.env.read());
        setPrototype(vm.objectPrototype);
    }

    private static Object requireKey(Object k) {
        if (!(k instanceof NgsObject) && !(k instanceof org.ngscript.value.SymbolValue)) {
            throw new IllegalArgumentException("WeakMap key must be an object");
        }
        return k;
    }

    @Override
    public MemRef lookup(String member, Machine vm, boolean isMember) throws NgScriptRuntimeException {
        switch (member) {
            case "set":
                return new MemRef((Invokable) (m, a) -> {
                    entries.put(requireKey(a[0]), a.length > 1 ? a[1] : Undefined.value);
                    m.eax.write(m.thisRef.read());
                });
            case "get":
                return new MemRef((Invokable) (m, a) -> {
                    Object v = entries.get(a[0]);
                    m.eax.write(v == null ? Undefined.value : v);
                });
            case "has":
                return new MemRef((Invokable) (m, a) -> m.eax.write(entries.containsKey(a[0])));
            case "delete":
                return new MemRef((Invokable) (m, a) -> m.eax.write(entries.remove(a[0]) != null));
            default:
                return super.lookup(member, vm, isMember);
        }
    }

    /** The WeakMap constructor (new WeakMap(iterable)). */
    public static class Ctor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            NgsWeakMap wm = new NgsWeakMap(vm);
            if (args.length > 0 && args[0] != null && args[0] != Undefined.value) {
                try {
                    java.util.Iterator<Object> it = OpUtils.toJavaIterator(vm,
                            OpUtils.getIterator(vm, args[0]));
                    while (it.hasNext()) {
                        Object pair = it.next();
                        Object k = OpUtils.getMember(vm, pair, "0");
                        Object v = OpUtils.getMember(vm, pair, "1");
                        wm.entries.put(requireKey(k), v == null ? Undefined.value : v);
                    }
                } catch (NgScriptRuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    // keep the original exception type (TypeError for bad keys)
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
            vm.eax.write(wm);
        }
    }
}

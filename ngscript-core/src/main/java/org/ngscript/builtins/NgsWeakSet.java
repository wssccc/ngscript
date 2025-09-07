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
 * The ES {@code WeakSet} builtin: object members only (weak references, no
 * iteration), with add/has/delete. The constructor accepts an iterable.
 *
 * @author wssccc
 */
public class NgsWeakSet extends NgsObject {

    private final java.util.Set<Object> members = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<>());

    public NgsWeakSet(Machine vm) {
        super((NgsObject) vm.env.read());
        setPrototype(vm.objectPrototype);
    }

    private static Object requireMember(Object v) {
        if (!(v instanceof NgsObject) && !(v instanceof org.ngscript.value.SymbolValue)) {
            throw new IllegalArgumentException("WeakSet member must be an object");
        }
        return v;
    }

    @Override
    public MemRef lookup(String member, Machine vm, boolean isMember) throws NgScriptRuntimeException {
        switch (member) {
            case "add":
                return new MemRef((Invokable) (m, a) -> {
                    members.add(requireMember(a[0]));
                    m.eax.write(m.thisRef.read());
                });
            case "has":
                return new MemRef((Invokable) (m, a) -> m.eax.write(members.contains(a[0])));
            case "delete":
                return new MemRef((Invokable) (m, a) -> m.eax.write(members.remove(a[0])));
            default:
                return super.lookup(member, vm, isMember);
        }
    }

    /** The WeakSet constructor (new WeakSet(iterable)). */
    public static class Ctor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            NgsWeakSet ws = new NgsWeakSet(vm);
            if (args.length > 0 && args[0] != null && args[0] != Undefined.value) {
                try {
                    java.util.Iterator<Object> it = OpUtils.toJavaIterator(vm,
                            OpUtils.getIterator(vm, args[0]));
                    while (it.hasNext()) {
                        ws.members.add(requireMember(it.next()));
                    }
                } catch (NgScriptRuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    // keep the original exception type (TypeError for bad members)
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
            vm.eax.write(ws);
        }
    }
}

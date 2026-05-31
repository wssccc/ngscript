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

package org.ngscript.runtime;

import java.util.ArrayList;

import org.ngscript.value.MemRef;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.SymbolValue;
import org.ngscript.value.Undefined;

/**
 * Array opcodes: literal construction and indexed access.
 *
 * @author wssccc
 */
public class OpArray {

    /**
     * Converts an index value to a property key string; symbols use their
     * canonical internal key ("@@iterator" etc.).
     */
    public static String keyString(Object key) {
        if (key instanceof SymbolValue) {
            return ((SymbolValue) key).getId();
        }
        return org.ngscript.runtime.OpUtils.toStringValue(key);
    }

    public static void array_new(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        int length = Integer.parseInt(param);
        NgsArray list = new NgsArray((NgsObject) vm.env.read());
        list.setPrototype(vm.arrayPrototype);

        for (int i = 0; i < length; i++) {
            Object object = vm.stack.pop();
            list.set("" + i, object);
        }
        vm.eax.write(list);
    }

    /**
     * Write-mode bracket reference ({@code a[k] = v}): like
     * {@link #array_ref} but a null/undefined base resolves the reference
     * without throwing — the TypeError fires at the assignment (PutValue),
     * after the right-hand side has evaluated (ES evaluation order).
     */
    public static void array_ref_w(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (objs[0] == null || objs[0] == Undefined.value) {
            final Object base = objs[0];
            final String key = keyString(objs[1]);
            vm.eax.write(new MemRef(Undefined.value) {
                @Override
                public void write(Object v) {
                    throw new IllegalArgumentException("Cannot set property '" + key
                            + "' of " + (base == null ? "null" : "undefined"));
                }
            });
            return;
        }
        arrayRefInner(vm, objs, keyString(objs[1]));
    }

    public static void array_ref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        arrayRefInner(vm, objs, keyString(objs[1]));
    }

    private static void arrayRefInner(Machine vm, Object[] objs, String member) throws NgScriptRuntimeException {
        if (objs[0] instanceof NgsObject) {
            NgsObject env = (NgsObject) objs[0];
            if (objs[0] instanceof NgsArray
                    && !"length".equals(member)
                    && NgsArray.isNumericKey(member)) {
                // numeric index access on an array: resolve in write mode so
                // a write (a[3] = v) goes through set() and invalidates the
                // virtual length cache; reads of missing indices still yield
                // undefined (the transient reference reads as undefined)
                vm.eax.write(((NgsArray) objs[0]).lookupForWrite(member, vm));
                return;
            }
            MemRef ref = env.lookup(member, vm, true);
            vm.eax.write(ref);
            return;
        }
        if (objs[0] instanceof ArrayList) {
            ArrayList env = (ArrayList) objs[0];
            int idx = (int) Double.parseDouble(member);
            MemRef ref = (MemRef) env.get(idx);
            vm.eax.write(ref);
            return;
        }
        if (objs[0] instanceof String) {
            // string indexing by code unit ('xyz'[1] === 'y')
            String s = (String) objs[0];
            int idx = (int) Double.parseDouble(member);
            if (idx >= 0 && idx < s.length()) {
                vm.eax.write(new MemRef(s.substring(idx, idx + 1)));
            } else {
                vm.eax.write(new MemRef(Undefined.value));
            }
            return;
        }
        if (objs[0] == null || objs[0] == Undefined.value) {
            // ES: reading a property of null/undefined is a TypeError
            throw new IllegalArgumentException(
                    "Cannot read properties of " + (objs[0] == null ? "null" : "undefined")
                            + " (reading '" + member + "')");
        }
        if (objs[0] instanceof ScriptFunction) {
            // bracket access on a script function: class-defined static
            // members first, then the intrinsic function properties
            java.util.Map<String, MemRef> own = vm.hostProps.get(objs[0]);
            if (own != null) {
                MemRef dyn = own.get(member);
                if (dyn != null) {
                    vm.eax.write(dyn);
                    return;
                }
            }
            MemRef ref = org.ngscript.runtime.OpObject.functionMember(
                    (ScriptFunction) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
            ref = org.ngscript.runtime.OpObject.objectProtoMember(member);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
            vm.eax.write(new MemRef(Undefined.value));
            return;
        }
        throw new NgScriptRuntimeException(vm, "not an array object");
    }
}

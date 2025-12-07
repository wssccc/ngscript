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
        return "" + key;
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

    public static void array_ref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (objs[0] instanceof NgsObject) {
            NgsObject env = (NgsObject) objs[0];
            String member = keyString(objs[1]);
            MemRef ref = env.lookup(member, vm, true);
            vm.eax.write(ref);
            return;
        }
        if (objs[0] instanceof ArrayList) {
            ArrayList env = (ArrayList) objs[0];
            int member = (int) Double.parseDouble("" + objs[1]);
            MemRef ref = (MemRef) env.get(member);
            vm.eax.write(ref);
            return;
        }
        if (objs[0] instanceof String) {
            // string indexing by code unit ('xyz'[1] === 'y')
            String s = (String) objs[0];
            int idx = (int) Double.parseDouble("" + objs[1]);
            if (idx >= 0 && idx < s.length()) {
                vm.eax.write(new MemRef(s.substring(idx, idx + 1)));
            } else {
                vm.eax.write(new MemRef(Undefined.value));
            }
            return;
        }
        if (objs[0] instanceof ScriptFunction) {
            // bracket access on a script function: class-defined static
            // members first, then the intrinsic function properties
            String member = keyString(objs[1]);
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
            vm.eax.write(new MemRef(Undefined.value));
            return;
        }
        throw new NgScriptRuntimeException(vm, "not an array object");
    }
}

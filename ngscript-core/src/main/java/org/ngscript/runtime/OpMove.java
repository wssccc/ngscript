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

import org.ngscript.value.MemRef;
import org.ngscript.value.Undefined;

/**
 * Move opcodes: register and variable loads/stores.
 *
 * @author wssccc
 */
public class OpMove {

    public static void deref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef v1;
        if (param.equals("%eax")) {
            v1 = (MemRef) vm.eax.read();
        } else {
            v1 = vm.lookup(param);
        }
        vm.eax.write(v1.read());
    }

    /**
     * Safe variable read for {@code typeof}: an undeclared name yields the
     * undefined value (typeof undeclared === "undefined"); a TDZ let/const
     * still throws (typeof on a TDZ binding is a ReferenceError).
     */
    public static void typeof_name(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        try {
            MemRef v1 = vm.lookup(param);
            vm.eax.write(v1.read());
        } catch (NgScriptRuntimeException e) {
            if (e.getMessage() != null && e.getMessage().endsWith(" is not declared")) {
                vm.eax.write(Undefined.value);
                return;
            }
            throw e;
        }
    }

    public static void mov_eax(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef v1 = vm.lookup(param);
        vm.eax.write(v1);
    }

    /**
     * Assignment reference load: like {@link #mov_eax} but an undeclared
     * name creates an implicit global binding (sloppy-mode assignment
     * semantics: {@code x = 1} on an undeclared x assigns to a new global
     * instead of throwing). Reads (deref) still throw for undeclared names.
     */
    public static void mov_eax_or_create(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        try {
            vm.eax.write(vm.lookup(param));
        } catch (NgScriptRuntimeException e) {
            if (e.getMessage() != null && e.getMessage().endsWith(" is not declared")) {
                NgsObject root = ((NgsObject) vm.env.read()).rootFrame();
                MemRef ref = new MemRef(Undefined.value);
                root.putRef(param, ref);
                vm.eax.write(ref);
                return;
            }
            throw e;
        }
    }

    public static void mov_exception_eax(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.exception.write(vm.eax.read());
    }

    public static void mov_eax_exception(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(vm.exception.read());
    }

    public static void mov(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {

        MemRef v1 = vm.lookup(param);
        Object val;
        if (param_extend.charAt(0) == '[') {
            //value
            String varName = param_extend.substring(1, param_extend.length() - 1);
            MemRef v2 = vm.lookup(varName);
            //is register
            if (varName.equals("%eax") || varName.equals("%exception") || varName.equals("%env")) {
                v2 = (MemRef) v2.read();
            }
            val = v2.read();
        } else {
            //ref
            MemRef v2 = vm.lookup(param_extend);
            if (param_extend.equals("%eax") || param_extend.equals("%exception") || param_extend.equals("%env")) {
                val = v2.read();
            } else {
                val = v2;
            }
        }
        v1.write(val);
    }
}

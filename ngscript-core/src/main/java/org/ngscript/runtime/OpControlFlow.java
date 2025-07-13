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

import org.ngscript.value.Undefined;

/**
 * Control-flow opcodes: jumps, labels, the go body, halt, return and call
 * stack cleanup.
 *
 * @author wssccc
 */
public class OpControlFlow {

    public static void jmp(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        if ("offset".equals(param)) {
            int nip = (Integer) vm.eax.read();
            vm.setEip(nip + Integer.parseInt(param_extend));
        } else {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where " + param);
            }
        }
    }

    public static void jz(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object testObj = vm.eax.read();
        if (!OpUtils.testValue(testObj)) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where");
            }
        }
    }

    public static void jnz(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object testObj = vm.eax.read();
        if (OpUtils.testValue(testObj)) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where");
            }
        }
    }

    /**
     * Jumps when the current value is neither null nor undefined (nullish
     * check, used by {@code ??=}).
     */
    public static void jnn(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object testObj = vm.eax.read();
        if (testObj != null && testObj != Undefined.value) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where");
            }
        }
    }

    public static void label(Machine vm, String param, String param_extend) {
        //
    }

    public static void clear_call_stack(Machine vm, String param, String param_extend) {
        while (vm.callstack.size() > vm.callStackSize) {
            vm.callstack.pop();
        }
    }

    public static void ret(Machine vm, String param, String param_extend) {
        int nip = (Integer) vm.stack.pop();
        vm.callstack.pop();
        vm.setEip(nip);
    }

    public static void go(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        // the go body shares the vm stack, so it must run synchronously on the current thread:
        // save the current position, jump into the go body and run until it halts, then resume.
        int savedEip = vm.getEip();
        jmp(vm, param, param_extend);
        vm.run();
        vm.setEip(savedEip);
    }

    public static void halt(Machine vm, String param, String param_extend) {
        vm.setEip(-1);
    }
}

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
 * Stack opcodes: pushes, pops, peeks, argument picking and packing.
 *
 * @author wssccc
 */
public class OpStack {

    public static void push_eax(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.stack.push(vm.eax.read());
    }

    public static void push_env(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.stack.push(vm.env.read());
    }

    public static void push_eip(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.stack.push(vm.getEip());
    }

    public static void pop_eax(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(vm.stack.pop());
    }

    public static void pop_env(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.env.write(vm.stack.pop());
    }

    public static void pop(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.stack.pop();
    }

    public static void peek(Machine vm, String param, String param_extend) {
        int offset = Integer.parseInt(param);
        Object obj = vm.stack.peek(offset);
        vm.eax.write(obj);
    }

    public static void pickarg(Machine vm, String param, String param_extend) {
        int offset = Integer.parseInt(param);
        //stack layout inside a function: [receiver, func, args[], env, retAddr]
        Object[] args = (Object[]) vm.stack.peek(2);
        if (offset < args.length) {
            vm.eax.write(args[offset]);
        } else {
            vm.eax.write(Undefined.value);
        }
    }

    public static void packargs(Machine vm, String param, String param_extend) {
        int n = Integer.parseInt(param);
        Object[] args = new Object[n];
        for (int i = 0; i < n; i++) {
            args[i] = vm.stack.pop();
        }
        vm.eax.write(args);
    }
}

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

import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
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

    /**
     * Loads argument {@code param} as a shared reference (mapped arguments
     * semantics): the MemRef is stored back into the args array so the
     * arguments object built by {@link #rest_args} aliases the parameter
     * binding. Writing through either side updates both.
     */
    public static void pickarg_ref(Machine vm, String param, String param_extend) {
        int offset = Integer.parseInt(param);
        //stack layout inside a function: [receiver, func, args[], env, retAddr]
        Object[] args = (Object[]) vm.stack.peek(2);
        MemRef ref;
        if (offset < args.length) {
            Object v = args[offset];
            if (v instanceof MemRef) {
                ref = (MemRef) v;
            } else {
                ref = new MemRef(v);
                args[offset] = ref;
            }
        } else {
            ref = new MemRef(Undefined.value);
        }
        vm.eax.write(ref);
    }

    public static void packargs(Machine vm, String param, String param_extend) {
        int n = Integer.parseInt(param);
        Object[] args = new Object[n];
        for (int i = 0; i < n; i++) {
            args[i] = vm.stack.pop();
        }
        vm.eax.write(args);
    }

    /** Builds the rest parameter array from argument index {@code param}. */
    public static void rest_args(Machine vm, String param, String param_extend) {
        int from = Integer.parseInt(param);
        Object[] args = (Object[]) vm.stack.peek(2);
        NgsArray arr = new NgsArray((NgsObject) vm.env.read());
        // the arguments object's [[Prototype]] is Object.prototype (ES); it
        // stays array-like via its own numeric keys and length
        arr.setPrototype(vm.objectPrototype);
        // ... but it still iterates like an array (Symbol.iterator)
        arr.set("@@iterator", new MemRef((Invokable) (v, a) -> {
            NgsArray self = arr;
            self.resetIterator();
            v.eax.write(self);
        }));
        for (int i = from; i < args.length; i++) {
            Object v = args[i];
            if (v instanceof MemRef) {
                // mapped arguments: share the parameter's reference so
                // arguments[i] aliases the parameter binding
                arr.putRef("" + (i - from), (MemRef) v);
            } else {
                arr.set("" + (i - from), v);
            }
        }
        vm.eax.write(arr);
    }

    /** Pushes an empty argument collector (a {@link java.util.ArrayList}). */
    public static void args_array_new(Machine vm, String param, String param_extend) {
        vm.stack.push(new java.util.ArrayList<>());
    }

    /** Appends the current eax value to the collector on the stack top. */
    public static void args_add(Machine vm, String param, String param_extend) {
        java.util.ArrayList<Object> list = (java.util.ArrayList<Object>) vm.stack.pop();
        list.add(vm.eax.read());
        vm.stack.push(list);
    }

    /** Spreads the iterable in eax into the collector on the stack top. */
    public static void args_spread_add(Machine vm, String param, String param_extend) throws Exception {
        java.util.ArrayList<Object> list = (java.util.ArrayList<Object>) vm.stack.pop();
        Object iterator = OpUtils.getIterator(vm, vm.eax.read());
        if (iterator instanceof java.util.Iterator) {
            java.util.Iterator it = (java.util.Iterator) iterator;
            long budget = vm.runBudget;
            long n = 0;
            while (it.hasNext()) {
                if (budget >= 0 && ++n > budget) {
                    throw new NgScriptRuntimeException(vm,
                            "instruction budget exceeded while spreading an iterator");
                }
                list.add(it.next());
            }
        } else {
            // generic ES iterator protocol: poll next() until done
            long budget = vm.runBudget;
            long n = 0;
            while (true) {
                if (budget >= 0 && ++n > budget) {
                    throw new NgScriptRuntimeException(vm,
                            "instruction budget exceeded while spreading an iterator");
                }
                Object method = OpUtils.getMember(vm, iterator, "next");
                Object result = OpUtils.callValue(vm, method, iterator, new Object[0]);
                if (result instanceof NgsObject) {
                    if (!OpUtils.testValue(((NgsObject) result).get("done"))) {
                        list.add(((NgsObject) result).get("value"));
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        vm.stack.push(list);
    }

    /** Converts the collector to a call argument array. */
    public static void args_pack(Machine vm, String param, String param_extend) {
        java.util.ArrayList<?> list = (java.util.ArrayList<?>) vm.stack.pop();
        vm.eax.write(list.toArray(new Object[0]));
    }

    /** Converts the collector to an array literal value. */
    public static void args_to_array(Machine vm, String param, String param_extend) {
        java.util.ArrayList<?> list = (java.util.ArrayList<?>) vm.stack.pop();
        NgsArray arr = new NgsArray((NgsObject) vm.env.read());
        arr.setPrototype(vm.arrayPrototype);
        for (int i = 0; i < list.size(); i++) {
            arr.set("" + i, list.get(i));
        }
        vm.eax.write(arr);
    }
}

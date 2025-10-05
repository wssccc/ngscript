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

import org.ngscript.value.ScriptFunction;
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

    /**
     * Jumps when the current value is exactly undefined (used by default
     * parameter initializers; null does not trigger the default).
     */
    public static void jundef(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        if (vm.eax.read() == Undefined.value) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where");
            }
        }
    }

    /**
     * Jumps when the current value is not exactly undefined (the complement
     * of {@link #jundef}).
     */
    public static void jnundef(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        if (vm.eax.read() != Undefined.value) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where");
            }
        }
    }

    /**
     * Jumps when eax is null or undefined, writing undefined to eax first
     * (the ES optional-chaining short-circuit value).
     */
    public static void jnullish(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object v = vm.eax.read();
        if (v == null || v == Undefined.value) {
            vm.eax.write(Undefined.value);
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
        // restore the caller's this binding before popping the call frame
        if (!vm.callstack.isEmpty()) {
            ScriptFunction top = vm.callstack.peek();
            vm.thisRef.write(top.savedThis);
        }
        vm.callstack.pop();
        if (nip == -1) {
            // generator body completion: record the return value before the
            // run loop pops the saved context
            try {
                Object g = ((NgsObject) vm.env.read()).lookup("%generator", vm, false).read();
                if (g instanceof Generator) {
                    ((Generator) g).complete(vm.eax.read());
                }
            } catch (Throwable ignored) {
                // plain scripts and go coroutines also return to the -1 halt
                // sentinel without a %generator binding
            }
        }
        vm.setEip(nip);
    }

    /**
     * Suspends the running generator body, yielding the value in eax (the
     * yield expression evaluates to the value passed to the next next()).
     */
    public static void yield_op(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        try {
            Object g = ((NgsObject) vm.env.read()).lookup("%generator", vm, false).read();
            if (g instanceof Generator) {
                // drop the generator's callstack entry (resume re-pushes it)
                vm.callstack.pop();
                ((Generator) g).suspend(vm.eax.read(), false);
                vm.setEip(-1);
                return;
            }
        } catch (NgScriptRuntimeException ignored) {
            // fall through: yield outside a generator
        }
        throw new NgScriptRuntimeException(vm, "yield outside a generator");
    }

    /**
     * Suspends the generator for a {@code yield*} delegation: resumes pull
     * values from the delegate iterator (in eax) until it is exhausted.
     * The first value is yielded immediately.
     */
    public static void yield_delegate(Machine vm, String param, String param_extend) throws Exception {
        Object g = ((NgsObject) vm.env.read()).lookup("%generator", vm, false).read();
        if (g instanceof Generator) {
            Generator gen = (Generator) g;
            vm.callstack.pop();
            gen.setDelegate(OpUtils.getIterator(vm, vm.eax.read()));
            Object first = gen.delegateNext();
            if (first == null) {
                // empty delegate: continue after the yield* expression
                vm.eax.write(Undefined.value);
                return;
            }
            gen.suspend(first, true);
            vm.setEip(-1);
            return;
        }
        throw new NgScriptRuntimeException(vm, "yield* outside a generator");
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

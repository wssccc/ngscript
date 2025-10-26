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
 * The execution context of an async function body. Calling an async
 * function creates the context and runs the body synchronously on the
 * machine until the first await suspends it; the promise the caller
 * receives settles when the body completes. Suspension saves the body's
 * call frame and any partial expression state off the live stack (the
 * same technique as {@link Generator}); a microtask restores the frame
 * and continues the body loop, feeding the awaited value to the await
 * expression.
 *
 * @author wssccc
 */
public class AsyncContext {

    private final Machine vm;
    private final ScriptFunction fn;
    private final Object[] args;
    private Object[] frameSlot;
    private int resumeEip;
    private int callerEip;
    private int callerStackSize;
    private NgsObject asyncEnv;
    private NgsObject callerEnv;
    private boolean done;
    private Object result;
    private Throwable thrown;

    /** The promise returned to the async function's caller. */
    public final org.ngscript.builtins.PromiseBuiltins.PromiseValue promise;

    public AsyncContext(Machine vm, ScriptFunction fn, Object[] args) {
        this.vm = vm;
        this.fn = fn;
        this.args = args;
        this.promise = new org.ngscript.builtins.PromiseBuiltins.PromiseValue(vm);
    }

    /**
     * Starts the async body: saves the caller position, pushes the call
     * frame (marked with %async), and jumps into the body. Returns true
     * when the body completed synchronously (the promise is already
     * settled), false when it suspended at an await.
     */
    public boolean start() throws Exception {
        callerEip = vm.getEip();
        callerStackSize = vm.stack.size();
        callerEnv = (NgsObject) vm.env.read();
        vm.callstack.push(fn);
        fn.savedThis = vm.thisRef.read();
        NgsObject frame = new NgsObject((NgsObject) fn.environment);
        frame.functionScope = true;
        frame.set("%async", this);
        asyncEnv = frame;
        vm.env.write(frame);
        vm.stack.push(null);   // receiver
        vm.stack.push(fn);     // func
        vm.stack.push(args);   // args
        vm.stack.push(frame);  // env
        vm.stack.push(-1);     // return address: halt sentinel
        vm.setEip(vm.labels.get(fn.functionLabel));
        runBodyLoop();
        // hand control back to the caller: its instruction position, env
        // and stack (the async frame and partial expression state are
        // stripped; the caller's own 4-slot call frame stays untouched)
        vm.setEip(callerEip);
        vm.env.write(callerEnv);
        while (vm.stack.size() > callerStackSize) {
            vm.stack.pop();
        }
        if (done) {
            promise.resolve(result);
            return true;
        }
        return false;
    }

    /** Runs the async body's own instruction loop until it suspends or returns. */
    private void runBodyLoop() throws NgScriptRuntimeException {
        while (true) {
            int eip = vm.getEip();
            if (eip < 0 || eip >= vm.instructions.length) {
                break;
            }
            vm.setEip(eip + 1);
            try {
                vm.instructions[eip].invoke(vm);
            } catch (Throwable ex) {
                // an exception escapes the async body: settle the promise
                // as rejected (unless a try/catch in the body handled it,
                // which would have restored the machine state itself)
                thrown = ex;
                done = true;
                // the frame remains on the stack; strip it so the caller
                // resumes cleanly
                vm.setEip(-1);
                break;
            }
        }
        if (done && thrown != null) {
            try {
                promise.reject(thrown);
            } catch (Throwable ignored) {
                // already settled
            }
        }
    }

    /**
     * Suspends the body at an await: pops the async frame and partial
     * expression state off the live stack, saving the whole segment.
     */
    public void suspend() {
        resumeEip = vm.getEip();
        int n = vm.stack.size() - callerStackSize;
        frameSlot = new Object[n];
        for (int i = n - 1; i >= 0; i--) {
            frameSlot[i] = vm.stack.pop();
        }
        vm.setEip(-1);
    }

    /**
     * Resumes the body after the awaited promise settled; {@code value}
     * becomes the await expression's value.
     */
    public void resume(Object value) {
        if (done) {
            return;
        }
        callerStackSize = vm.stack.size();
        callerEnv = (NgsObject) vm.env.read();
        vm.callstack.push(fn);
        vm.env.write(asyncEnv);
        for (Object slot : frameSlot) {
            vm.stack.push(slot);
        }
        vm.eax.write(value);
        vm.setEip(resumeEip);
        try {
            runBodyLoop();
        } catch (Throwable ignored) {
            // runBodyLoop settles the promise on its own
        }
        if (done) {
            if (thrown != null) {
                try {
                    promise.reject(thrown);
                } catch (Throwable ignored) {
                    // already settled
                }
            } else {
                try {
                    promise.resolve(result);
                } catch (Throwable ignored) {
                    // already settled
                }
            }
        }
    }

    /**
     * Resumes the body after an awaited promise rejected: the body resumes
     * and the rejection reason is thrown at the await expression (an
     * enclosing try/catch in the body sees it; otherwise the async promise
     * rejects with the same reason).
     */
    public void resumeThrowing(Throwable reason) {
        if (done) {
            return;
        }
        callerStackSize = vm.stack.size();
        callerEnv = (NgsObject) vm.env.read();
        vm.callstack.push(fn);
        vm.env.write(asyncEnv);
        // restore only the async call frame (receiver, func, args, env, -1);
        // the await expression's partial state (assignment targets etc.) is
        // dropped: an exception path re-enters through the saved try context
        for (int i = 0; i < 5 && i < frameSlot.length; i++) {
            vm.stack.push(frameSlot[i]);
        }
        vm.exception.write(reason);
        vm.setEip(resumeEip);
        try {
            // re-enter the body: if the await sat inside a try/catch, the
            // saved machine state is restored and the catch clause runs
            // from there; without one, the restore throws and the rejection
            // escapes to the catch below
            OpCall.restore_machine_state(vm, null, null);
            runBodyLoop();
        } catch (Throwable ex) {
            thrown = ex;
            done = true;
            vm.setEip(-1);
            try {
                promise.reject(ex);
            } catch (Throwable ignored) {
                // already settled
            }
        }
        if (done && thrown == null) {
            // the body caught the rejection and completed normally
            try {
                promise.resolve(result);
            } catch (Throwable ignored) {
                // already settled
            }
        }
    }

    /**
     * Called by ret when the async body returns: the value in eax is the
     * completion value.
     */
    public void complete(Object returnValue) {
        done = true;
        result = returnValue;
    }

    /** Whether the body has finished. */
    public boolean isDone() {
        return done;
    }

    /** The value the body completed with (or the thrown exception). */
    public Object getResult() {
        return thrown != null ? thrown : result;
    }
}

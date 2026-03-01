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
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * An ES generator iterator: created when a {@code function*} is called. The
 * body runs lazily on {@code next()}; a yield pops the generator's call
 * frame off the live machine stack and records it, so the caller resumes
 * cleanly (resume pushes the frame back). The generator object exposes
 * {@code next}/{@code return}/{@code throw} and {@code Symbol.iterator}
 * (it is its own iterator).
 *
 * @author wssccc
 */
public class Generator extends NgsObject {

    private final Machine vm;
    private final ScriptFunction fn;
    private final Object[] args;
    private Object[] frameSlot;
    private int resumeEip;
    private int callerEip;
    private int callerStackSize;
    private NgsObject genEnv;
    private NgsObject callerEnv;
    private boolean yielded;
    private boolean closed;
    private boolean delegating;
    private Object delegate;
    private Object lastResult;
    private final Object receiver;

    public Generator(Machine vm, ScriptFunction fn, Object[] args) {
        this(vm, fn, args, null);
    }

    public Generator(Machine vm, ScriptFunction fn, Object[] args, Object receiver) {
        super((NgsObject) fn.environment);
        this.vm = vm;
        this.fn = fn;
        this.args = args;
        this.receiver = receiver;
        setPrototype(vm.objectPrototype);
        set("next", (Invokable) (m, a) ->
                m.eax.write(nextResult(a != null && a.length > 0 ? a[0] : Undefined.value)));
        set("return", (Invokable) (m, a) -> {
            closed = true;
            yielded = false;
            m.eax.write(resultObject(a != null && a.length > 0 ? a[0] : Undefined.value, true));
        });
        set("throw", (Invokable) (m, a) -> {
            closed = true;
            yielded = false;
            if (a != null && a.length > 0 && a[0] instanceof RuntimeException) {
                throw (RuntimeException) a[0];
            }
            throw new RuntimeException("generator throw");
        });
        set("@@iterator", (Invokable) (m, a) -> m.eax.write(this));
    }

    /** Whether the generator has finished (returned or been closed). */
    public boolean isClosed() {
        return closed;
    }

    private static NgsObject resultObject(Object value, boolean done) {
        NgsObject r = new NgsObject(null);
        r.set("value", value);
        r.set("done", done);
        return r;
    }

    /**
     * Advances the generator: runs the body from the start (first call) or
     * from the last yield, passing {@code sent} as the yield expression's
     * value. Returns an ES iterator result object.
     */
    public NgsObject nextResult(Object sent) throws Exception {
        if (closed) {
            return resultObject(Undefined.value, true);
        }
        yielded = false;
        if (frameSlot == null) {
            start();
        } else {
            resume(sent);
        }
        runBodyLoop();
        // hand control back to the caller: instruction position, env and
        // stack (the for-of/iterator paths have no pop_env of their own)
        vm.setEip(callerEip);
        vm.env.write(callerEnv);
        // strip whatever remains of the generator's frame and partial
        // expression state off the live stack
        while (vm.stack.size() > callerStackSize) {
            vm.stack.pop();
        }
        return resultObject(lastResult, !yielded);
    }

    /**
     * The generator's own instruction loop (not {@link Machine#run}, which
     * would resume the caller): runs until a yield suspends the body (eip
     * -1) or the body returns.
     */
    private void runBodyLoop() throws NgScriptRuntimeException {
        // isolate the exception-state stack: a throw inside the generator
        // body must propagate to the caller as a wrapped value, not consume
        // the caller's try contexts via restore_machine_state (the body's
        // restore would otherwise resume the caller's code inside this
        // loop, corrupting the machine stack)
        org.ngscript.util.FastStack<Context> savedMss = vm.isolateMss();
        try {
            runBodyLoopInner();
        } finally {
            vm.restoreMss(savedMss);
        }
    }

    private void runBodyLoopInner() throws NgScriptRuntimeException {
        while (true) {
            int eip = vm.getEip();
            if (eip < 0) {
                break;
            }
            if (eip >= vm.instructions.length) {
                break;
            }
            vm.setEip(eip + 1);
            try {
                vm.instructions[eip].invoke(vm);
            } catch (Throwable ex) {
                try {
                    // unwrap script values so a JS catch binds the original
                    // thrown object (iterator errors from destructuring)
                    vm.exception.write(OpCall.unwrapThrown(ex));
                    OpCall.restore_machine_state(vm, null, null);
                } catch (NgScriptRuntimeException re) {
                    // no enclosing JS try: propagate to the next() caller
                    closed = true;
                    yielded = false;
                    throw re;
                }
            }
        }
    }

    /** Sets up the initial call frame and jumps to the body. */
    private void start() {
        callerEip = vm.getEip();
        callerStackSize = vm.stack.size();
        callerEnv = (NgsObject) vm.env.read();
        vm.callstack.push(fn);
        fn.savedThis = vm.thisRef.read();
        // the generator body sees the call-time receiver as this (arrows
        // keep their lexical this via capturedThis)
        vm.thisRef.write(fn.arrow
                ? (fn.capturedThis != null ? fn.capturedThis : Undefined.value)
                : (receiver != null ? receiver : Undefined.value));
        NgsObject frame = new NgsObject((NgsObject) fn.environment);
        frame.functionScope = true;
        frame.set("%generator", this);
        genEnv = frame;
        vm.env.write(frame);
        vm.stack.push(receiver); // receiver
        vm.stack.push(fn);   // func
        vm.stack.push(args); // args
        vm.stack.push(frame); // env
        vm.stack.push(-1);    // return address: the halt sentinel
        vm.setEip(vm.labels.get(fn.functionLabel));
    }

    /**
     * Runs the parameter-binding prefix of the generator body at call time
     * (ES: FunctionDeclarationInstantiation happens when the generator is
     * called; only the body is deferred to next()). Stops at the
     * {@code gen_body_enter} marker, leaving the machine in the caller's
     * state with the frame recorded for the first next(). A throw during
     * binding propagates to the caller (the frame is stripped).
     */
    public void bindParams() throws NgScriptRuntimeException {
        callerEip = vm.getEip();
        callerStackSize = vm.stack.size();
        callerEnv = (NgsObject) vm.env.read();
        vm.callstack.push(fn);
        fn.savedThis = vm.thisRef.read();
        // the generator body sees the call-time receiver as this (arrows
        // keep their lexical this via capturedThis)
        vm.thisRef.write(fn.arrow
                ? (fn.capturedThis != null ? fn.capturedThis : Undefined.value)
                : (receiver != null ? receiver : Undefined.value));
        NgsObject frame = new NgsObject((NgsObject) fn.environment);
        frame.functionScope = true;
        frame.set("%generator", this);
        genEnv = frame;
        vm.env.write(frame);
        vm.stack.push(receiver); // receiver
        vm.stack.push(fn);   // func
        vm.stack.push(args); // args
        vm.stack.push(frame); // env
        vm.stack.push(-1);    // return address: the halt sentinel
        Integer nipObj = vm.labels.get(fn.functionLabel);
        vm.setEip(nipObj == null ? 0 : nipObj);
        // isolate the exception-state stack like runBodyLoop: a throw
        // during parameter binding must not consume the caller's try
        // contexts (the binding runs synchronously at call time)
        org.ngscript.util.FastStack<Context> savedMss = vm.isolateMss();
        boolean bound = false;
        try {
            while (true) {
                int eip = vm.getEip();
                if (eip < 0 || eip >= vm.instructions.length) {
                    break;
                }
                vm.setEip(eip + 1);
                try {
                    org.ngscript.runtime.opcache.OpBinding ins = vm.instructions[eip];
                    if (ins.op.equals("gen_body_enter")) {
                        // parameters are bound: record the resume point and the
                        // frame for the first next(), then restore the caller
                        bound = true;
                        resumeEip = vm.getEip();
                        int n = vm.stack.size() - callerStackSize;
                        frameSlot = new Object[n];
                        for (int i = n - 1; i >= 0; i--) {
                            frameSlot[i] = vm.stack.pop();
                        }
                        break;
                    }
                    ins.invoke(vm);
                } catch (Throwable ex) {
                    try {
                        vm.exception.write(OpCall.unwrapThrown(ex));
                        OpCall.restore_machine_state(vm, null, null);
                    } catch (NgScriptRuntimeException re) {
                        closed = true;
                        yielded = false;
                        vm.setEip(callerEip);
                        vm.env.write(callerEnv);
                        while (vm.stack.size() > callerStackSize) {
                            vm.stack.pop();
                        }
                        throw re;
                    }
                    // a JS try/catch handled it: the machine state (eip, env,
                    // stack) has been restored to the catch site; the frame was
                    // already popped by restore. Return without touching state.
                    return;
                }
            }
        } finally {
            vm.restoreMss(savedMss);
        }
        if (bound) {
            vm.setEip(callerEip);
            vm.env.write(callerEnv);
            while (vm.stack.size() > callerStackSize) {
                vm.stack.pop();
            }
        }
    }

    /** Restores the suspended frame; {@code sent} becomes the yield value. */
    private void resume(Object sent) throws Exception {
        callerEip = vm.getEip();
        callerStackSize = vm.stack.size();
        callerEnv = (NgsObject) vm.env.read();
        vm.callstack.push(fn);
        // the generator body keeps the call-time receiver as this across
        // suspensions (next()/return() must not rebind it)
        vm.thisRef.write(fn.arrow
                ? (fn.capturedThis != null ? fn.capturedThis : Undefined.value)
                : (receiver != null ? receiver : Undefined.value));
        vm.env.write(genEnv);
        for (Object slot : frameSlot) {
            vm.stack.push(slot);
        }
        vm.setEip(resumeEip);
        if (delegating) {
            // continue a yield* delegation
            Object next = delegateNext();
            if (next == null) {
                // exhausted: continue after the yield_delegate opcode
                delegating = false;
                vm.eax.write(Undefined.value);
            } else {
                suspend(next, true);
                // the delegated value is the result of this next() call
                vm.setEip(-1);
            }
        } else {
            vm.eax.write(sent);
        }
    }

    /**
     * Suspends the body at a yield: pops the generator's call frame AND any
     * partial expression state (assignment targets, binary operands) off the
     * live stack, recording the whole segment (the caller's frame stays
     * untouched so its cleanup sees exactly what it pushed).
     */
    public void suspend(Object value, boolean delegating) {
        this.delegating = delegating;
        resumeEip = vm.getEip();
        int n = vm.stack.size() - callerStackSize;
        frameSlot = new Object[n];
        for (int i = n - 1; i >= 0; i--) {
            frameSlot[i] = vm.stack.pop();
        }
        yielded = true;
        lastResult = value;
    }

    /** Called by ret when the generator body returns: the value is the result. */
    public void complete(Object returnValue) {
        yielded = false;
        closed = true;
        lastResult = returnValue;
    }

    /** Pulls the next delegation value (null when exhausted). */
    public Object delegateNext() throws Exception {
        if (delegate instanceof java.util.Iterator) {
            java.util.Iterator it = (java.util.Iterator) delegate;
            return it.hasNext() ? it.next() : null;
        }
        Object method = OpUtils.getMember(vm, delegate, "next");
        Object result = OpUtils.callValue(vm, method, delegate, new Object[0]);
        if (result instanceof NgsObject && !OpUtils.testValue(((NgsObject) result).get("done"))) {
            return ((NgsObject) result).get("value");
        }
        return null;
    }

    /** Sets the delegation iterator (used by the yield_delegate opcode). */
    public void setDelegate(Object iterator) {
        this.delegate = iterator;
    }
}

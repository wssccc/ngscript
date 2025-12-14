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

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ngscript.bytecode.Instruction;
import org.ngscript.runtime.opcache.OpBinding;
import org.ngscript.runtime.opcache.OpInvokable;
import org.ngscript.runtime.opcache.OpMap;
import org.ngscript.util.FastStack;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * @author wssccc
 */
public class Machine {

    Map<String, Method> cpuMethodCache = new HashMap<>();
    //static data
    OpBinding[] instructions = new OpBinding[0];
    Map<String, Integer> labels = new HashMap<>();

    Map<String, String> imported = new HashMap<>();
    FastStack<Context> machineStateStack = new FastStack<>(32);
    FastStack<Context> contextStack = new FastStack<>(32);

    /**
     * Script-created properties on host (non-NgsObject) objects, e.g.
     * {@code list.customProp = 1} on a Java ArrayList. Keyed by object
     * identity; cleaned up with the machine.
     */
    public final java.util.Map<Object, java.util.Map<String, MemRef>> hostProps =
            new java.util.IdentityHashMap<>();

    /** Deep snapshot of {@link #hostProps} (runner isolation between cases). */
    public java.util.Map<Object, java.util.Map<String, MemRef>> snapshotHostProps() {
        java.util.Map<Object, java.util.Map<String, MemRef>> snap =
                new java.util.IdentityHashMap<>();
        for (java.util.Map.Entry<Object, java.util.Map<String, MemRef>> e : hostProps.entrySet()) {
            snap.put(e.getKey(), new java.util.HashMap<>(e.getValue()));
        }
        return snap;
    }

    /** Restores {@link #hostProps} from a snapshot taken by {@link #snapshotHostProps}. */
    public void restoreHostProps(java.util.Map<Object, java.util.Map<String, MemRef>> snapshot) {
        hostProps.clear();
        for (java.util.Map.Entry<Object, java.util.Map<String, MemRef>> e : snapshot.entrySet()) {
            java.util.Map<String, MemRef> fresh = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, MemRef> m : e.getValue().entrySet()) {
                MemRef ref = m.getValue();
                fresh.put(m.getKey(), new MemRef(ref.read(), ref.isImmutable()));
            }
            hostProps.put(e.getKey(), fresh);
        }
    }

    /**
     * Shared built-in prototype objects: array literals link to
     * {@link #arrayPrototype}, object literals to {@link #objectPrototype}.
     * Set up by {@link org.ngscript.builtins.Builtins#register} at
     * construction time.
     */
    public NgsObject arrayPrototype;
    public NgsObject objectPrototype;

    //machine states
    Instruction hints;
    FastStack<Object> stack = new FastStack<>(32);
    FastStack<ScriptFunction> callstack = new FastStack<ScriptFunction>(32);
    //temp var for clear callStackSize op
    int callStackSize;
    //registers
    public final MemRef eax = new MemRef();
    public final MemRef exception = new MemRef();

    public final MemRef env = new MemRef();

    /**
     * The {@code this} value of the currently executing function call,
     * bound by the call opcode (method receivers) and new_op (instances).
     */
    public final MemRef thisRef = new MemRef();

    /**
     * The saved {@code this} stack for class static field initializers
     * (push_this/pop_this). Kept off the data stack so field-initializer
     * instructions (peek 0) see the constructor as the stack top.
     */
    private final java.util.Deque<Object> savedThisStack = new java.util.ArrayDeque<>();

    public void pushSavedThis() {
        savedThisStack.push(thisRef.read());
    }

    public void popSavedThis() {
        thisRef.write(savedThisStack.pop());
    }

    private ThreadLocal<Integer> eip = ThreadLocal.withInitial(() -> 0);
    //

    /**
     * The environment as built at construction time (builtins registered);
     * script executions create their own root scope on top of this baseline.
     */
    NgsObject initialEnv;

    /**
     * The shared global object (the builtins baseline), for globalThis.
     * Static so builtins can reach it without an instance reference.
     */
    public static volatile NgsObject globalThis;

    /**
     * The eval hook installed by NgScriptEngine: evaluates a source string
     * and returns its value (null when no engine is attached). The second
     * argument is the caller's current environment (the direct-eval scope);
     * a null caller means a global/indirect eval that runs in a fresh root
     * scope on the baseline environment.
     */
    public static volatile java.util.function.BiFunction<String, NgsObject, Object> evalHook;

    /**
     * Depth of replace-style executions (direct eval / CompiledScript) on
     * this machine. Closures created while it is > 0 get marked as eval
     * closures so calls can dispatch by their own instruction array.
     */
    public int execNesting;

    /**
     * The ES microtask queue (Promise reactions): tasks are drained when
     * the outermost run loop completes. Nested synchronous calls must not
     * drain (they would flush the queue mid-call).
     */
    private final java.util.ArrayDeque<Runnable> microtasks = new java.util.ArrayDeque<>();

    /** Enqueues a microtask (Promise reaction). */
    public void enqueueMicrotask(Runnable task) {
        microtasks.addLast(task);
    }

    /** Runs every queued microtask (new ones included, FIFO). */
    private void drainMicrotasks() {
        while (!microtasks.isEmpty()) {
            Runnable task = microtasks.pollFirst();
            try {
                task.run();
            } catch (Throwable ignored) {
                // unhandled rejection / broken reaction: swallow for now
            }
        }
    }

    PrintWriter out;
    PrintWriter err;

    public Machine(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
        //init register
        setEip(0);
        thisRef.write(Undefined.value);
        this.arrayPrototype = org.ngscript.builtins.Builtins.getArrayPrototype();
        this.objectPrototype = org.ngscript.builtins.Builtins.getObjectPrototype();
        env.write(new NgsObject(null));
        ((NgsObject) env.read()).functionScope = true;
        ((NgsObject) env.read()).registerBuiltins(out);
        initialEnv = (NgsObject) env.read();
        Machine.globalThis = initialEnv;
    }

    /**
     * The baseline environment with all builtins registered.
     */
    public NgsObject getInitialEnvironment() {
        return initialEnv;
    }

    /**
     * Binds a compiled instruction list to opcode implementations without
     * merging it into the machine's current instruction array.
     */
    public OpBinding[] bindInstructions(List<Instruction> ins2) {
        Map<String, OpInvokable> map = OpMap.INSTANCE.getMap();
        ArrayList<OpBinding> ins = new ArrayList<>();
        for (Instruction instruction : ins2) {
            ins.add(new OpBinding(instruction, map.get(instruction.op)));
        }
        return ins.toArray(new OpBinding[0]);
    }

    /**
     * Replaces the current instruction array with the given bound instructions
     * and rebuilds the label table. Used for isolated script execution.
     */
    public void installInstructions(OpBinding[] bound) {
        instructions = bound;
        labels.clear();
        updateLabels();
    }

    /**
     * Resets execution registers and stacks in preparation for running a
     * freshly installed instruction array from the start.
     */
    public void resetForExecution() {
        hints = null;
        setEip(0);
        eax.write(null);
        exception.write(null);
        stack.clear();
        callstack.clear();
    }

    public void printEax(boolean colorize) {
        if (eax.read() != null) {
            out.println((colorize ? "[[b;white;black]%eax] = " : "%eax = ") + ((eax.read() == null ? "null" : eax.read()) + " (" + (eax.read() == null ? "null" : (eax.read().getClass().isAnonymousClass() ? eax.read().getClass().getSuperclass().getSimpleName() : eax.read().getClass().getSimpleName()))) + ")");
        }
    }

    public MemRef lookup(String member) throws NgScriptRuntimeException {
        return ((NgsObject) env.read()).lookup(member, this, false);
    }

    public Class[] getParamTypes(int offset) {
        Object[] params = (Object[]) stack.peek(offset);
        Class types[] = new Class[params.length];
        for (int i = 0; i < types.length; i++) {
            if (params[i] == null) {
                types[i] = null;
            } else {
                types[i] = params[i].getClass();
            }
        }

        return types;
    }

    public void loadInstructions(List<Instruction> ins2) {
        Map<String, OpInvokable> map = OpMap.INSTANCE.getMap();
        //
        ArrayList<OpBinding> ins = new ArrayList<>();
        for (Instruction instruction : ins2) {
            ins.add(new OpBinding(instruction, map.get(instruction.op)));
        }
        //
        OpBinding[] opBindings = ins.toArray(new OpBinding[0]);
        OpBinding[] merged = new OpBinding[opBindings.length + instructions.length];
        System.arraycopy(instructions, 0, merged, 0, instructions.length);
        System.arraycopy(opBindings, 0, merged, instructions.length, opBindings.length);
        instructions = merged;
        updateLabels();
    }

    void updateLabels() {
        for (int i = 0; i < instructions.length; ++i) {
            Instruction instruction = instructions[i];
            if ("label".equals(instruction.op)) {
                labels.put(instruction.param, i);
            }
        }
    }

    /**
     * Instruction budget protecting against runaway loops (infinite
     * while/for/generator loops): when >= 0 the run loop aborts after that
     * many executed instructions. The runner sets it per case; normal
     * scripting leaves it at -1.
     */
    /**
     * Whether the last destructuring next() reported done (drives
     * IteratorClose: a done iterator is not closed).
     */
    public boolean destrDone;

    public long runBudget = -1;

    /**
     * The Machine running on the current thread (used by property getters
     * invoked from {@link NgsObject#get} which have no Machine parameter).
     */
    private static final ThreadLocal<Machine> CURRENT = new ThreadLocal<>();

    public static Machine getCurrent() {
        return CURRENT.get();
    }

    public void run() throws NgScriptRuntimeException {
        //initial
        exception.write(null);
        eax.write(null);
        Machine prev = CURRENT.get();
        CURRENT.set(this);
        try {
            runInner();
        } finally {
            CURRENT.set(prev);
        }
    }

    private void runInner() throws NgScriptRuntimeException {
        long budget = runBudget;
        while (true) {
            if (getEip() < 0 || getEip() >= instructions.length) {
                if (!contextStack.isEmpty()) {
                    Context lastContext = contextStack.pop();
                    lastContext.restore(this);
                } else {
                    // the outermost run completes: flush any queued Promise
                    // microtasks before returning to the caller
                    if (!microtasks.isEmpty()) {
                        drainMicrotasks();
                        continue;
                    }
                    return;
                }
            }

            if (budget >= 0 && --budget < 0) {
                throw new NgScriptRuntimeException(this,
                        "instruction budget exceeded (possible infinite loop)");
            }
            OpBinding instruction = instructions[getEip()];
            setEip(getEip() + 1);
            if (instruction.op.equals("//")) {
                hints = instruction;
                continue;
            }
            try {
                instruction.invoke(this);
            } catch (ClosureBudgetExceeded cbe) {
                // a runaway eval closure must not be retried: propagate it
                // to the caller immediately (the case fails fast)
                throw cbe;
            } catch (Throwable ex) {
                try {
                    exception.write(ex);
                    OpCall.restore_machine_state(this, null, null);
                } catch (NgScriptRuntimeException ex1) {
                    err.println("VM Exception");
                    err.println(ex1);
                    throw ex1;
                }
            }
        }
    }

    public int getEip() {
        return eip.get();
    }

    /**
     * Sentinel return address used by {@link #callFunctionSync} so the nested
     * instruction loop knows when the callee has returned.
     */
    private static final int SYNC_CALL_RET = -2;

    /**
     * Calls a script function created inside an eval whose execution has
     * ended: switches to the closure's own instruction array (and its label
     * table), runs the body in a nested loop, then restores the machine to
     * the caller's state, including its instruction array. {@code frame}
     * is the caller-prepared function frame (its env); a null frame is
     * built from the closure's captured environment.
     */
    public Object callClosureSync(ScriptFunction c, Object[] args, Object receiver,
                                  org.ngscript.runtime.opcache.OpBinding[] ins,
                                  NgsObject frame) throws Exception {
        int savedEip = getEip();
        int savedStackSize = stack.size();
        int savedCallStackSize = callstack.size();
        Object savedThis = thisRef.read();
        NgsObject savedEnv = (NgsObject) env.read();
        OpBinding[] savedIns = instructions;
        Map<String, Integer> savedLabels = labels;
        instructions = ins;
        labels = new HashMap<>();
        updateLabels();
        FastStack<Context> savedMss = machineStateStack;
        machineStateStack = new FastStack<>(8);
        try {
            callstack.push(c);
            if (frame == null) {
                frame = new NgsObject((NgsObject) c.environment);
                frame.functionScope = true;
            }
            env.write(frame);
            if (receiver == OpCall.NEW_RECEIVER) {
                // new-expression call: the frame is the instance (mirrors
                // the plain call path); link constructor/prototype so the
                // constructor body can use `this instanceof Ctor`
                thisRef.write(frame);
                frame.setConstructor(c);
                frame.setPrototype(c.getPrototypeObject());
            } else {
                thisRef.write(c.arrow
                        ? (c.capturedThis != null ? c.capturedThis : Undefined.value)
                        : (receiver != null ? receiver : Undefined.value));
            }
            stack.push(receiver != null ? receiver : null);
            stack.push(c);
            stack.push(args);
            stack.push(frame);
            stack.push(SYNC_CALL_RET);
            setEip(labels.get(c.functionLabel));
            long budget = runBudget;
            while (true) {
                if (budget >= 0 && --budget < 0) {
                    // a runaway closure: fail fast instead of letting the
                    // enclosing run loop retry (which would burn the whole
                    // budget again); ClosureBudgetExceeded propagates
                    // through OpCall.call untouched
                    throw new ClosureBudgetExceeded(this,
                            "closure instruction budget exceeded for "
                                    + (c.name == null ? "anonymous" : c.name)
                                    + " label=" + c.functionLabel);
                }
                int eip = getEip();
                if (eip < 0 || eip >= instructions.length) {
                    break;
                }
                setEip(eip + 1);
                instructions[eip].invoke(this);
            }
            return eax.read();
        } finally {
            while (stack.size() > savedStackSize) {
                stack.pop();
            }
            while (callstack.size() > savedCallStackSize) {
                callstack.pop();
            }
            env.write(savedEnv);
            thisRef.write(savedThis);
            setEip(savedEip);
            instructions = savedIns;
            labels = savedLabels;
            machineStateStack = savedMss;
        }
    }

    /**
     * Calls a script function synchronously from a builtin (e.g. an Array
     * iteration callback): pushes the call frame, executes the function body
     * instructions inline, then restores the machine state. Supports nested
     * calls because the instruction loop follows eip like the main loop.
     */
    public Object callFunctionSync(Object callee, Object[] args, Object receiver) throws Exception {
        int savedEip = getEip();
        int savedStackSize = stack.size();
        int savedCallStackSize = callstack.size();
        Object savedThis = thisRef.read();
        NgsObject savedEnv = (NgsObject) env.read();
        Machine prev = CURRENT.get();
        CURRENT.set(this);
        try {
            return callFunctionSyncInner(callee, args, receiver,
                    savedEip, savedStackSize, savedCallStackSize, savedThis, savedEnv);
        } finally {
            CURRENT.set(prev);
        }
    }

    private Object callFunctionSyncInner(Object callee, Object[] args, Object receiver,
                                         int savedEip, int savedStackSize, int savedCallStackSize,
                                         Object savedThis, NgsObject savedEnv) throws Exception {
        if (callee instanceof ScriptFunction) {
            ScriptFunction c = (ScriptFunction) callee;
            callstack.push(c);
            NgsObject frame = new NgsObject((NgsObject) c.environment);
            frame.functionScope = true;
            env.write(frame);
            // arrows keep their lexical this; ordinary functions bind the receiver
            thisRef.write(c.arrow
                    ? (c.capturedThis != null ? c.capturedThis : Undefined.value)
                    : (receiver != null ? receiver : Undefined.value));
            stack.push(receiver != null ? receiver : null);
            stack.push(callee);
            stack.push(args);
            stack.push(frame);
            stack.push(SYNC_CALL_RET);
            setEip(labels.get(c.functionLabel));
            // isolate the exception-state stack for the duration of the
            // synchronous call: a throw inside the callee (a getter or
            // callback) must propagate out of the call, so the caller's
            // run loop routes it to the nearest enclosing try/catch of the
            // *call site* (the caller's Contexts are restored afterwards).
            // Without this, restore_machine_state would consume the caller's
            // Context here and swallow the exception.
            FastStack<Context> savedMss = machineStateStack;
            machineStateStack = new FastStack<>(8);
            try {
                while (true) {
                    int eip = getEip();
                    if (eip < 0 || eip >= instructions.length) {
                        break;
                    }
                    setEip(eip + 1);
                    instructions[eip].invoke(this);
                }
            } finally {
                while (stack.size() > savedStackSize) {
                    stack.pop();
                }
                while (callstack.size() > savedCallStackSize) {
                    callstack.pop();
                }
                env.write(savedEnv);
                thisRef.write(savedThis);
                setEip(savedEip);
                machineStateStack = savedMss;
            }
            return eax.read();
        }
        if (callee instanceof Invokable) {
            ((Invokable) callee).invoke(this, args);
            return eax.read();
        }
        throw new NgScriptRuntimeException(this, "not a function");
    }

    public void setEip(int eip) {
        this.eip.set(eip);
    }

    /**
     * Captures the full machine state: instructions, labels, registers and
     * stacks. Used to isolate script executions from the surrounding state.
     */
    public MachineState captureState() {
        return new MachineState(this);
    }

    /**
     * Restores a previously captured state, undoing any mutation performed
     * by an isolated execution.
     */
    public void restoreState(MachineState state) {
        state.applyTo(this);
    }

    /**
     * An immutable snapshot of a {@link Machine}.
     */
    public static class MachineState {

        final OpBinding[] instructions;
        final Map<String, Integer> labels;
        final Instruction hints;
        final int eip;
        final Object eax;
        final Object exception;
        final Object env;
        final Object thisValue;
        final Object[] stack;
        final Object[] callstack;
        final java.util.ArrayDeque<Runnable> microtasks;
        final int machineStateStackSize;

        MachineState(Machine vm) {
            this.instructions = vm.instructions;
            this.labels = new HashMap<>(vm.labels);
            this.hints = vm.hints;
            this.eip = vm.getEip();
            this.eax = vm.eax.read();
            this.exception = vm.exception.read();
            this.env = vm.env.read();
            this.thisValue = vm.thisRef.read();
            this.stack = new Object[vm.stack.size()];
            for (int i = 0; i < this.stack.length; i++) {
                this.stack[i] = vm.stack.get(i);
            }
            this.callstack = new Object[vm.callstack.size()];
            for (int i = 0; i < this.callstack.length; i++) {
                this.callstack[i] = vm.callstack.get(i);
            }
            this.microtasks = new java.util.ArrayDeque<>(vm.microtasks);
            this.machineStateStackSize = vm.machineStateStack.size();
        }

        void applyTo(Machine vm) {
            vm.instructions = instructions;
            vm.labels.clear();
            vm.labels.putAll(labels);
            vm.hints = hints;
            vm.setEip(eip);
            vm.eax.write(eax);
            vm.exception.write(exception);
            vm.env.write(env);
            vm.thisRef.write(thisValue);
            vm.stack.clear();
            for (Object o : stack) {
                vm.stack.push(o);
            }
            vm.callstack.clear();
            for (Object o : callstack) {
                vm.callstack.push((ScriptFunction) o);
            }
            // the microtask queue and the exception-state stack belong to the
            // surrounding context: restore them so no task leaks between
            // isolated executions
            vm.microtasks.clear();
            vm.microtasks.addAll(microtasks);
            while (vm.machineStateStack.size() > machineStateStackSize) {
                vm.machineStateStack.pop();
            }
        }
    }
}

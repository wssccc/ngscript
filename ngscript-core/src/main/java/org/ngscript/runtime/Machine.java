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
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.MemRef;
import org.ngscript.util.FastStack;

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
    private ThreadLocal<Integer> eip = ThreadLocal.withInitial(() -> 0);
    //

    /**
     * The environment as built at construction time (builtins registered);
     * script executions create their own root scope on top of this baseline.
     */
    NgsObject initialEnv;

    PrintWriter out;
    PrintWriter err;

    public Machine(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
        //init register
        setEip(0);
        env.write(new NgsObject(null));
        ((NgsObject) env.read()).registerBuiltins(out);
        initialEnv = (NgsObject) env.read();
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

    public void run() throws NgScriptRuntimeException {
        //initial
        exception.write(null);
        eax.write(null);
        while (true) {
            if (getEip() < 0 || getEip() >= instructions.length) {
                if (!contextStack.isEmpty()) {
                    Context lastContext = contextStack.pop();
                    lastContext.restore(this);
                } else {
                    return;
                }
            }

            OpBinding instruction = instructions[getEip()];
            setEip(getEip() + 1);
            if (instruction.op.equals("//")) {
                hints = instruction;
                continue;
            }
            try {
                instruction.invoke(this);
            } catch (Exception ex) {
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
        final Object[] stack;
        final Object[] callstack;

        MachineState(Machine vm) {
            this.instructions = vm.instructions;
            this.labels = new HashMap<>(vm.labels);
            this.hints = vm.hints;
            this.eip = vm.getEip();
            this.eax = vm.eax.read();
            this.exception = vm.exception.read();
            this.env = vm.env.read();
            this.stack = new Object[vm.stack.size()];
            for (int i = 0; i < this.stack.length; i++) {
                this.stack[i] = vm.stack.get(i);
            }
            this.callstack = new Object[vm.callstack.size()];
            for (int i = 0; i < this.callstack.length; i++) {
                this.callstack[i] = vm.callstack.get(i);
            }
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
            vm.stack.clear();
            for (Object o : stack) {
                vm.stack.push(o);
            }
            vm.callstack.clear();
            for (Object o : callstack) {
                vm.callstack.push((ScriptFunction) o);
            }
        }
    }
}

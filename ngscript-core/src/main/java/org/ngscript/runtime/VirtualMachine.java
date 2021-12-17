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

import org.ngscript.compiler.Instruction;
import org.ngscript.runtime.opcache.OpBinding;
import org.ngscript.runtime.opcache.OpMap;
import org.ngscript.runtime.vo.FunctionDef;
import org.ngscript.runtime.vo.VmMemRef;
import org.ngscript.runtime.vo.VmMethod;
import org.ngscript.utils.FastStack;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wssccc
 */
public class VirtualMachine {

    Map<String, Method> cpuMethodCache = new HashMap<>();
    //static data
    OpBinding[] instructions = new OpBinding[0];
    Map<String, Integer> labels = new HashMap<>();

    Map<String, String> imported = new HashMap<>();
    FastStack<Context> machine_state_stack = new FastStack<>(32);
    FastStack<Context> contextStack = new FastStack<>(32);

    //machine states
    Instruction helptext;
    FastStack<Object> stack = new FastStack<>(32);
    FastStack<FunctionDef> callstack = new FastStack<FunctionDef>(32);
    //temp var for clear callStackSize op
    int call_stack_size;
    //registers
    public final VmMemRef eax = new VmMemRef();
    public final VmMemRef exception = new VmMemRef();

    public final VmMemRef env = new VmMemRef();
    private ThreadLocal<Integer> eip = ThreadLocal.withInitial(() -> 0);
    //

    PrintWriter out;
    PrintWriter err;

    public VirtualMachine(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
        //init register
        setEip(0);
        env.write(new Environment(null));
        init_builtins(((Environment) env.read()).data);
        //init_builtins(func);
    }

    public void printEax(boolean highlight) {
        if (eax.read() != null) {
            out.println((highlight ? "[[b;white;black]%eax] = " : "%eax = ") + ((eax.read() == null ? "null" : eax.read()) + " (" + (eax.read() == null ? "null" : (eax.read().getClass().isAnonymousClass() ? eax.read().getClass().getSuperclass().getSimpleName() : eax.read().getClass().getSimpleName()))) + ")");
        }
    }

    public VmMemRef lookup(String member) throws VmRuntimeException {
        return ((Environment) env.read()).lookup(member, this, false);
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
        Map<String, InvokableInstruction> map = OpMap.INSTANCE.getMap();
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

    void init_builtins(Map<String, VmMemRef> map) {
        map.put("println", new VmMemRef(new VmMethod() {
            @Override
            public void invoke(VirtualMachine vm, Object[] vars) {
                for (Object var : vars) {
                    out.print(var);
                }
                out.println();
                out.flush();
            }
        }));
        map.put("print", new VmMemRef(new VmMethod() {
            @Override
            public void invoke(VirtualMachine vm, Object[] vars) {
                for (Object var : vars) {
                    out.print(var);
                }
                out.flush();
            }
        }));
        map.put("assert", new VmMemRef(new VmMethod() {
            @Override
            public void invoke(VirtualMachine vm, Object[] vars) {
                for (Object var : vars) {
                    if (!OpUtils.testValue(var)) {
                        throw new VmRuntimeException("Assertion failed");
                    }
                }
            }
        }));
        map.put("Object", new VmMemRef(new VmMethod() {
            @Override
            public void invoke(VirtualMachine vm, Object[] vars) {
                //prepare env
                Environment env = new Environment((Environment) vm.env.read());
                vm.env.write(env);
            }
        }));
        map.put("Coroutine", new VmMemRef(Coroutine.class));
        //init coroutine return-hook
//        instructions.add(new Instruction("jmp", "coroutine_return_hook_exit"));
//        instructions.add(new Instruction("coroutine_return"));
//        labels.put("coroutine_return_hook", 1);
//        labels.put("coroutine_return_hook_exit", instructions.size());
    }

    public void run() throws VmRuntimeException {
        //initial
        exception.write(null);
        eax.write(null);
        while (true) {
            if (getEip() < 0 || getEip() >= instructions.length) {
                //halted, try upper context
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
                helptext = instruction;
                continue;
            }
            //System.out.println("eval " + eip + "\t" + instruction);
            try {
                //instant accleration
                //AutoCreatedCpuDispatcher.dispatch(instruction, this);
                instruction.invoke(this);
//                if (AutoCreatedCpuDispatcher.dispatch(instruction, this)) {
//                    continue;
//                }
//                Method m;
//                if (cpuMethodCache.containsKey(instruction.op)) {
//                    m = cpuMethodCache.get(instruction.op);
//                } else {
//                    m = Op.class.getMethod(instruction.op, VirtualMachine.class, String.class, String.class);
//                    cpuMethodCache.put(instruction.op, m);
//                }
//                m.invoke(Op.class, this, instruction.param, instruction.paramExtended);

            } catch (Exception ex) {
                try {
                    //System.out.println(ex.getCause().toString());
                    //for detail
                    exception.write(ex);
                    Op.restore_machine_state(this, null, null);
                } catch (VmRuntimeException ex1) {
                    err.println("VM Exception");
                    err.println(ex1.toString());
                    throw ex1; //do not hold this type
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
}

/*
 *  wssccc all rights reserved
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class VirtualMachine {

    HashMap<String, Method> cpuMethodCache = new HashMap<String, Method>();
    //static data
    OpBinding[] instructions = new OpBinding[0];
    HashMap<String, Integer> labels = new HashMap<String, Integer>();

    HashMap<String, String> imported = new HashMap<String, String>();
    FastStack<Context> machine_state_stack = new FastStack<Context>(32);
    FastStack<Context> contextStack = new FastStack<Context>(32);

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
    int eip = 0;
    //

    PrintWriter out;
    PrintWriter err;

    public VirtualMachine(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
        //init register
        eip = 0;
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

    void init_builtins(HashMap<String, VmMemRef> map) {
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

    public void run() throws InvocationTargetException, VmRuntimeException, Exception {
        //initial
        exception.write(null);
        eax.write(null);
        while (true) {
            if (eip < 0 || eip >= instructions.length) {
                //halted, try upper context
                if (!contextStack.isEmpty()) {
                    Context lastContext = contextStack.pop();
                    lastContext.restore(this);
                } else {
                    return;
                }
            }

            OpBinding instruction = instructions[eip];
            ++eip;
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
                    System.out.println("eip=" + eip);
                    Logger.getLogger(VirtualMachine.class.getName()).log(Level.SEVERE, null, ex);
                    exception.write(ex);
                    Op.restore_machine_state(this, null, null);
                } catch (VmRuntimeException ex1) {
                    err.println("VM Exception");
                    err.println(ex1.toString());
                    Logger.getLogger(VirtualMachine.class.getName()).log(Level.SEVERE, null, ex1.getCause());
                    throw ex1; //do not hold this type
                }
            }
        }
    }
}

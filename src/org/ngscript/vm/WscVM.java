/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm;

import java.io.PrintWriter;
import org.ngscript.vm.structure.VmMemRef;
import org.ngscript.vm.structure.VmClosure;
import org.ngscript.common.Instruction;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ngscript.vm.cpu.AutoCreatedCpuDispatcher;
import org.ngscript.vm.structure.BuiltinClosure;
import org.ngscript.j2se.DrawWindow;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscVM {

    HashMap<String, Method> cpuMethodCache = new HashMap<String, Method>();
    //static data
    ArrayList<Instruction> instructions = new ArrayList<Instruction>();
    HashMap<String, Integer> labels = new HashMap<String, Integer>();

    HashMap<String, String> imported = new HashMap<String, String>();
    Stack<Context> machine_state_stack = new Stack<Context>();
    Stack<Context> contextStack = new Stack<Context>();

    //machine states
    Instruction helptext;
    Stack<Object> stack = new Stack<Object>();
    Stack<VmClosure> callstack = new Stack<VmClosure>();
    //temp var for clear call_stack_size op
    int call_stack_size;
    //registers
    public final VmMemRef eax = new VmMemRef();
    final VmMemRef exception = new VmMemRef();

    public final VmMemRef<ScopeHash> env = new VmMemRef();
    int eip = 0;
    //

    PrintWriter out;
    PrintWriter err;

    public WscVM(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
        //init register
        eip = 0;
        env.write(new ScopeHash(null));
        init_builtins((ScopeHash) env.read());
        //init_builtins(func);
    }

    public int getSize() {
        return instructions.size();
    }

    public void printEax(boolean highlight) {
        if (eax.read() != null) {
            out.println((highlight ? "[[b;white;black]%eax] = " : "%eax = ") + ((eax.read() == null ? "null" : eax.read()) + " (" + (eax.read() == null ? "null" : (eax.read().getClass().isAnonymousClass() ? eax.read().getClass().getSuperclass().getSimpleName() : eax.read().getClass().getSimpleName()))) + ")");
        }
    }

    public VmMemRef lookup(String member) throws WscVMException {
        return env.read().lookup(member, this, false);
    }

    Class[] getParamTypes(int offset) {
        LinkedList<Object> params = (LinkedList<Object>) stack.get(stack.size() - 1 - offset);
        Class types[] = new Class[params.size()];
        for (int i = 0; i < types.length; i++) {
            if (params.get(i) == null) {
                types[i] = null;
            } else {
                types[i] = params.get(i).getClass();
            }
        }

        return types;
    }

    public void loadInstructions(ArrayList<Instruction> ins) {
        loadLabels(ins);
        //reset eip
        eip = instructions.size();
        instructions.addAll(ins);
    }

    final void loadLabels(ArrayList<Instruction> ins) {
        int offset = instructions.size();
        for (int i = 0; i < ins.size(); ++i) {
            if (ins.get(i).op.equals("label")) {
                labels.put(ins.get(i).param, i + offset);
            }
        }
    }

    final void init_builtins(HashMap<String, VmMemRef> map) {
        map.put("println", new VmMemRef(new BuiltinClosure() {
            @Override
            public void invoke(WscVM vm, LinkedList<Object> vars) {
                for (Object var : vars) {
                    out.print(var);
                }
                out.println();
                out.flush();
            }
        }));
        map.put("showWindow", new VmMemRef(new BuiltinClosure() {
            @Override
            public void invoke(WscVM vm, LinkedList<Object> vars) {
                java.awt.EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        new DrawWindow().setVisible(true);
                    }
                });
            }
        }));
        map.put("draw", new VmMemRef(new BuiltinClosure() {
            @Override
            public void invoke(WscVM vm, LinkedList<Object> vars) {
                int x = (Integer) vars.get(0);
                int y = (Integer) vars.get(1);
                int r = (Integer) vars.get(2);
                int g = (Integer) vars.get(3);
                int b = (Integer) vars.get(4);
                DrawWindow.draw(x, y, r, g, b);
            }
        }));
        map.put("print", new VmMemRef(new BuiltinClosure() {
            @Override
            public void invoke(WscVM vm, LinkedList<Object> vars) {
                for (Object var : vars) {
                    out.print(var);
                }
                out.flush();
            }
        }));
        map.put("Object", new VmMemRef(new BuiltinClosure() {
            @Override
            public void invoke(WscVM vm, LinkedList<Object> vars) {
                //prepare env
                ScopeHash env = new ScopeHash((ScopeHash) vm.env.read());
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

    public void run() throws InvocationTargetException, WscVMException, Exception {
        //initial
        exception.write(null);
        eax.write(null);
        while (true) {
            if (eip < 0 || eip >= instructions.size()) {
                //halted, try upper context
                if (!contextStack.isEmpty()) {
                    Context lastContext = contextStack.pop();
                    lastContext.restore(this);
                } else {
                    return;
                }
            }

            Instruction instruction = instructions.get(eip);
            ++eip;
            if (instruction.op.equals("//")) {
                helptext = instruction;
                continue;
            }
            //System.out.println("run " + eip + "\t" + instruction);
            try {
                //instant accleration
                AutoCreatedCpuDispatcher.dispatch(instruction, this);
//                if (AutoCreatedCpuDispatcher.dispatch(instruction, this)) {
//                    continue;
//                }
//                Method m;
//                if (cpuMethodCache.containsKey(instruction.op)) {
//                    m = cpuMethodCache.get(instruction.op);
//                } else {
//                    m = VmCpu.class.getMethod(instruction.op, WscVM.class, String.class, String.class);
//                    cpuMethodCache.put(instruction.op, m);
//                }
//                m.invoke(VmCpu.class, this, instruction.param, instruction.param_extend);

            } catch (InvocationTargetException ex) {
                try {
                    //System.out.println(ex.getCause().toString());
                    //for detail
                    System.out.println("eip=" + eip);
                    Logger.getLogger(WscVM.class.getName()).log(Level.SEVERE, null, ex);
                    exception.write(ex.getCause());
                    VmCpu.restore_machine_state(this, null, null);
                } catch (WscVMException ex1) {
                    err.println("VM Exception");
                    err.println(ex1.toString());
                    Logger.getLogger(WscVM.class.getName()).log(Level.SEVERE, null, ex1.getCause());
                    throw ex1; //do not hold this type
                }
            } catch (Exception ex) {
                Logger.getLogger(WscVM.class.getName()).log(Level.SEVERE, null, ex);
                throw ex;
            }
        }
    }
}

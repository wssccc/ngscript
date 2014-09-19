/*
 *  wssccc all rights reserved
 */
package ngscript.vm;

import java.io.PrintWriter;
import ngscript.vm.strcuture.VmMemRef;
import ngscript.vm.strcuture.VmClosure;
import ngscript.common.Instruction;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import ngscript.WscLang;
import ngscript.vm.strcuture.BuiltinClosure;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscVM {

    int code_section = 0;
    //static data
    ArrayList<Instruction> instructions = new ArrayList<Instruction>();
    HashMap<String, Integer> labels = new HashMap<String, Integer>();

    HashMap<String, String> imported = new HashMap<String, String>();
    Stack<Context> machine_state_stack = new Stack<Context>();
    //machine states
    Instruction helptext;
    Stack<Object> stack = new Stack<Object>();
    Stack<VmClosure> callstack = new Stack<VmClosure>();
    //temp var for clear call_stack_size op
    int call_stack_size;
    //registers
    public final VmMemRef eax = new VmMemRef();
    final VmMemRef exception = new VmMemRef();

    public final VmMemRef env = new VmMemRef();
    int eip = 0;
    boolean halted = false;
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
    }

    public int getSize() {
        return instructions.size();
    }

    public void printEax(boolean highlight) {
        if (eax.read() != null) {
            out.println((highlight ? "[[b;white;black]%eax] = " : "%eax = ") + ((eax.read() == null ? "null" : eax.read()) + " (" + (eax.read() == null ? "null" : (eax.read().getClass().isAnonymousClass() ? eax.read().getClass().getSuperclass().getSimpleName() : eax.read().getClass().getSimpleName()))) + ")");
        }
    }

    VmMemRef lookup(String member) throws WscVMException {
        return lookup(member, false);
    }

    VmMemRef lookup(String member, boolean isMember) throws WscVMException {
        return ((ScopeHash) env.read()).lookup(member, this, isMember);
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
        map.put("Object", new VmMemRef(new BuiltinClosure() {
            @Override
            public void invoke(WscVM vm, LinkedList<Object> vars) {
                //prepare env
                ScopeHash env = new ScopeHash((ScopeHash) vm.env.read());
                vm.env.write(env);
            }
        }));
        map.put("eval", new VmMemRef(new BuiltinClosure() {
            @Override
            public void invoke(WscVM vm, LinkedList<Object> vars) throws Exception {
                if (vars.size() != 1) {
                    throw new WscVMException(vm, "eval takes 1 param");
                }

                String code = (String) vars.get(0) + ";";
                ArrayList<Instruction> ins = WscLang.staticCompile(code, "ECS:" + code_section);
                //manually asm
                //add header
                ins.add(0, new Instruction("jmp", "exit_code_section_" + code_section));
                ins.add(1, new Instruction("label", "begin_code_section_" + code_section));
                //add tail
                ins.add(new Instruction("ret"));
                ins.add(new Instruction("label", "exit_code_section_" + code_section));
                int oldeip = vm.eip;
                loadInstructions(ins);
                //jmp
                vm.stack.push(oldeip);
                vm.callstack.push(new VmClosure("begin_code_section_" + code_section, null, WscVM.this));
                int nip = vm.labels.get("begin_code_section_" + code_section);
                vm.eip = nip;
            }
        }));
        map.put("Coroutine", new VmMemRef(Coroutine.class));
        //init coroutine return-hook
        instructions.add(new Instruction("jmp", "coroutine_return_hook_exit"));
        instructions.add(new Instruction("coroutine_return"));
        labels.put("coroutine_return_hook", 1);
        labels.put("coroutine_return_hook_exit", instructions.size());
    }

    public void run() throws InvocationTargetException, WscVMException {
        run(-1);
    }

    public void run(long timeLimit) throws InvocationTargetException, WscVMException {
        long startTime = System.currentTimeMillis();
        //initial
        exception.write(null);
        eax.write(null);
        while (true) {
            if (timeLimit > 0) {
                if (System.currentTimeMillis() - startTime > timeLimit) {
                    System.out.println(System.currentTimeMillis() - startTime);
                    err.println("VM Time Limit Exceeded");
                    eip = instructions.size();
                    return;
                }
            }
            if (halted || eip >= instructions.size()) {
                //halt
                return;
            }
            Instruction instruction = instructions.get(eip);
            ++eip;
            if (instruction.op.equals("//")) {
                helptext = instruction;
                continue;
            }
            //System.out.println("run " + eip + "\t" + instruction);
            Method m;
            try {
                m = VmCpu.class.getMethod(instruction.op, WscVM.class, String.class, String.class);
                m.invoke(VmCpu.class, this, instruction.param, instruction.param_extend);
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
                    //Logger.getLogger(WscVM.class.getName()).log(Level.SEVERE, null, ex1);
                    throw ex1; //do not hold this type
                }
            } catch (NoSuchMethodException ex) {
                Logger.getLogger(WscVM.class.getName()).log(Level.SEVERE, null, ex);
                return;
            } catch (SecurityException ex) {
                Logger.getLogger(WscVM.class.getName()).log(Level.SEVERE, null, ex);
                return;
            } catch (IllegalAccessException ex) {
                Logger.getLogger(WscVM.class.getName()).log(Level.SEVERE, null, ex);
                return;
            } catch (IllegalArgumentException ex) {
                Logger.getLogger(WscVM.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
        }
    }
}

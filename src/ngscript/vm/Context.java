/*
 *  wssccc all rights reserved
 */
package ngscript.vm;

import java.util.Stack;
import ngscript.common.Instruction;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Context {

    Object eax;
    Object env;
    int eip;
    boolean halted;
    Instruction helptext;
    int stack_size;
    int call_stack_size;
    Stack<Object> stack;

    public Context(WscVM vm) {
        this.save(vm);
    }

    public final void save(WscVM vm) {
        this.eax = vm.eax.read();
        this.env = vm.env.read();
        this.eip = vm.eip;
        this.halted = vm.halted;
        this.helptext = vm.helptext;
        this.stack_size = vm.stack.size();
        this.call_stack_size = vm.callstack.size();
        this.stack = vm.stack;
    }

    public final void restore(WscVM vm) {
        vm.eax.write(eax);
        vm.env.write(env);
        vm.eip = eip;
        vm.halted = halted;
        vm.helptext = helptext;
        vm.stack = stack;
        //the following while loop is to ensure the stack is balanced when in a try catch block
        while (vm.stack.size() > stack_size) {
            vm.stack.pop();
        }
        vm.call_stack_size = call_stack_size;
    }
}

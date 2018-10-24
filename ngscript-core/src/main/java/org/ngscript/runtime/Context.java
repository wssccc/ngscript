/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime;

import org.ngscript.compiler.Instruction;
import org.ngscript.utils.FastStack;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Context {

    Object eax;
    Environment env;
    int eip;
    Instruction hint;
    int stackSize;
    int callStackSize;
    FastStack<Object> stack;

    public Context(VirtualMachine vm) {
        this.save(vm);
    }

    public final void save(VirtualMachine vm) {
        this.eax = vm.eax.read();
        this.env = (Environment) vm.env.read();
        this.eip = vm.eip;
        this.hint = vm.helptext;
        this.stackSize = vm.stack.size();
        this.callStackSize = vm.callstack.size();
        this.stack = vm.stack;
    }

    public final void restore(VirtualMachine vm) {
        vm.eax.write(eax);
        vm.env.write(env);
        vm.eip = eip;
        vm.helptext = hint;
        vm.stack = stack;
        //the following while loop is to ensure the stack is balanced when in a try catch block
        while (vm.stack.size() > stackSize) {
            vm.stack.pop();
        }
        vm.call_stack_size = callStackSize;
    }
}

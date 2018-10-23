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
    ScopeHash env;
    int eip;
    Instruction helptext;
    int stack_size;
    int call_stack_size;
    FastStack<Object> stack;

    public Context(VirtualMachine vm) {
        this.save(vm);
    }

    public final void save(VirtualMachine vm) {
        this.eax = vm.eax.read();
        this.env = vm.env.read();
        this.eip = vm.eip;
        this.helptext = vm.helptext;
        this.stack_size = vm.stack.size();
        this.call_stack_size = vm.callstack.size();
        this.stack = vm.stack;
    }

    public final void restore(VirtualMachine vm) {
        vm.eax.write(eax);
        vm.env.write(env);
        vm.eip = eip;
        vm.helptext = helptext;
        vm.stack = stack;
        //the following while loop is to ensure the stack is balanced when in a try catch block
        while (vm.stack.size() > stack_size) {
            vm.stack.pop();
        }
        vm.call_stack_size = call_stack_size;
    }
}

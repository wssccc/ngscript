/*
 *  wssccc all rights reserved
 */
package ngscript.vm;

import ngscript.common.Instruction;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class MachineState {

    Object eax;
    Object env;
    int eip;
    boolean halted;
    Instruction helptext;
    int stack_size;
    int call_stack_size;

    public MachineState(WscVM vm) {
        this.eax = vm.eax.read();
        this.env = vm.env.read();
        this.eip = vm.eip;
        this.halted = vm.halted;
        this.helptext = vm.helptext;
        this.stack_size = vm.stack.size();
        this.call_stack_size = vm.callstack.size();
    }

    public void writeTo(WscVM vm) {
        vm.eax.write(eax);
        vm.env.write(env);
        vm.eip = eip;
        vm.halted = halted;
        vm.helptext = helptext;
        while (vm.stack.size() > stack_size) {
            vm.stack.pop();
        }
        vm.call_stack_size = call_stack_size;
    }
}

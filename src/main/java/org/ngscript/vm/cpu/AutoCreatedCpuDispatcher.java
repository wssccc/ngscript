/* DO NOT WRITE ANYTHING MANUALLY */
package org.ngscript.vm.cpu;

import org.ngscript.common.Instruction;
import org.ngscript.vm.*;

public class AutoCreatedCpuDispatcher {
    public static boolean dispatch(Instruction instruction, WscVM vm) throws WscVMException, Exception {
        switch (instruction.op) {
            case "add":
                VmCpu.add(vm, instruction.param, instruction.paramExtended);
                return true;
            case "clear":
                VmCpu.clear(vm, instruction.param, instruction.paramExtended);
                return true;
            case "lt":
                VmCpu.lt(vm, instruction.param, instruction.paramExtended);
                return true;
            case "pop":
                VmCpu.pop(vm, instruction.param, instruction.paramExtended);
                return true;
            case "peek":
                VmCpu.peek(vm, instruction.param, instruction.paramExtended);
                return true;
            case "eq":
                VmCpu.eq(vm, instruction.param, instruction.paramExtended);
                return true;
            case "deref":
                VmCpu.deref(vm, instruction.param, instruction.paramExtended);
                return true;
            case "dec":
                VmCpu.dec(vm, instruction.param, instruction.paramExtended);
                return true;
            case "post_inc":
                VmCpu.post_inc(vm, instruction.param, instruction.paramExtended);
                return true;
            case "le":
                VmCpu.le(vm, instruction.param, instruction.paramExtended);
                return true;
            case "inc":
                VmCpu.inc(vm, instruction.param, instruction.paramExtended);
                return true;
            case "pop_eax":
                VmCpu.pop_eax(vm, instruction.param, instruction.paramExtended);
                return true;
            case "post_dec":
                VmCpu.post_dec(vm, instruction.param, instruction.paramExtended);
                return true;
            case "ge":
                VmCpu.ge(vm, instruction.param, instruction.paramExtended);
                return true;
            case "gt":
                VmCpu.gt(vm, instruction.param, instruction.paramExtended);
                return true;
            case "assign":
                VmCpu.assign(vm, instruction.param, instruction.paramExtended);
                return true;
            case "pop_env":
                VmCpu.pop_env(vm, instruction.param, instruction.paramExtended);
                return true;
            case "jmp":
                VmCpu.jmp(vm, instruction.param, instruction.paramExtended);
                return true;
            case "jz":
                VmCpu.jz(vm, instruction.param, instruction.paramExtended);
                return true;
            case "jnz":
                VmCpu.jnz(vm, instruction.param, instruction.paramExtended);
                return true;
            case "label":
                VmCpu.label(vm, instruction.param, instruction.paramExtended);
                return true;
            case "new_closure":
                VmCpu.new_closure(vm, instruction.param, instruction.paramExtended);
                return true;
            case "integer":
                VmCpu.integer(vm, instruction.param, instruction.paramExtended);
                return true;
            case "double_":
                VmCpu.double_(vm, instruction.param, instruction.paramExtended);
                return true;
            case "undefined":
                VmCpu.undefined(vm, instruction.param, instruction.paramExtended);
                return true;
            case "string":
                VmCpu.string(vm, instruction.param, instruction.paramExtended);
                return true;
            case "dequeue":
                VmCpu.dequeue(vm, instruction.param, instruction.paramExtended);
                return true;
            case "ret":
                VmCpu.ret(vm, instruction.param, instruction.paramExtended);
                return true;
            case "bit_and":
                VmCpu.bit_and(vm, instruction.param, instruction.paramExtended);
                return true;
            case "bit_or":
                VmCpu.bit_or(vm, instruction.param, instruction.paramExtended);
                return true;
            case "bit_xor":
                VmCpu.bit_xor(vm, instruction.param, instruction.paramExtended);
                return true;
            case "sub":
                VmCpu.sub(vm, instruction.param, instruction.paramExtended);
                return true;
            case "mul":
                VmCpu.mul(vm, instruction.param, instruction.paramExtended);
                return true;
            case "mod":
                VmCpu.mod(vm, instruction.param, instruction.paramExtended);
                return true;
            case "div":
                VmCpu.div(vm, instruction.param, instruction.paramExtended);
                return true;
            case "veq":
                VmCpu.veq(vm, instruction.param, instruction.paramExtended);
                return true;
            case "neq":
                VmCpu.neq(vm, instruction.param, instruction.paramExtended);
                return true;
            case "vneq":
                VmCpu.vneq(vm, instruction.param, instruction.paramExtended);
                return true;
            case "new_queue":
                VmCpu.new_queue(vm, instruction.param, instruction.paramExtended);
                return true;
            case "call":
                VmCpu.call(vm, instruction.param, instruction.paramExtended);
                return true;
            case "test":
                VmCpu.test(vm, instruction.param, instruction.paramExtended);
                return true;
            case "clear_null":
                VmCpu.clear_null(vm, instruction.param, instruction.paramExtended);
                return true;
            case "new_op":
                VmCpu.new_op(vm, instruction.param, instruction.paramExtended);
                return true;
            case "mov_exception_eax":
                VmCpu.mov_exception_eax(vm, instruction.param, instruction.paramExtended);
                return true;
            case "mov_eax_exception":
                VmCpu.mov_eax_exception(vm, instruction.param, instruction.paramExtended);
                return true;
            case "clear_call_stack":
                VmCpu.clear_call_stack(vm, instruction.param, instruction.paramExtended);
                return true;
            case "save_machine_state":
                VmCpu.save_machine_state(vm, instruction.param, instruction.paramExtended);
                return true;
            case "drop_machine_state":
                VmCpu.drop_machine_state(vm, instruction.param, instruction.paramExtended);
                return true;
            case "restore_machine_state":
                VmCpu.restore_machine_state(vm, instruction.param, instruction.paramExtended);
                return true;
            case "mov_eax":
                VmCpu.mov_eax(vm, instruction.param, instruction.paramExtended);
                return true;
            case "mov":
                VmCpu.mov(vm, instruction.param, instruction.paramExtended);
                return true;
            case "array_new":
                VmCpu.array_new(vm, instruction.param, instruction.paramExtended);
                return true;
            case "object_new":
                VmCpu.object_new(vm, instruction.param, instruction.paramExtended);
                return true;
            case "neg":
                VmCpu.neg(vm, instruction.param, instruction.paramExtended);
                return true;
            case "push_eax":
                VmCpu.push_eax(vm, instruction.param, instruction.paramExtended);
                return true;
            case "push_env":
                VmCpu.push_env(vm, instruction.param, instruction.paramExtended);
                return true;
            case "push_eip":
                VmCpu.push_eip(vm, instruction.param, instruction.paramExtended);
                return true;
            case "set_var":
                VmCpu.set_var(vm, instruction.param, instruction.paramExtended);
                return true;
            case "member_ref":
                VmCpu.member_ref(vm, instruction.param, instruction.paramExtended);
                return true;
            case "array_ref":
                VmCpu.array_ref(vm, instruction.param, instruction.paramExtended);
                return true;
            case "static_func":
                VmCpu.static_func(vm, instruction.param, instruction.paramExtended);
                return true;
            case "typeof":
                VmCpu.typeof(vm, instruction.param, instruction.paramExtended);
                return true;
            case "import_":
                VmCpu.import_(vm, instruction.param, instruction.paramExtended);
                return true;
        }
        return false;
    }
}
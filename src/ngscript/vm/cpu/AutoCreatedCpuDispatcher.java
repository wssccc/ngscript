/* DO NOT WRITE ANYTHING MANUALLY */package ngscript.vm.cpu;

import ngscript.common.Instruction;
import ngscript.vm.*;

public class AutoCreatedCpuDispatcher {

    public static boolean dispatch(Instruction instruction, WscVM vm) throws WscVMException, Exception {
        if (instruction.op.equals("add")) {
            VmCpu.add(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("clear")) {
            VmCpu.clear(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("lt")) {
            VmCpu.lt(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("pop")) {
            VmCpu.pop(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("peek")) {
            VmCpu.peek(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("eq")) {
            VmCpu.eq(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("deref")) {
            VmCpu.deref(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("mov_exception_eax")) {
            VmCpu.mov_exception_eax(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("mov_eax_exception")) {
            VmCpu.mov_eax_exception(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("mov_eax")) {
            VmCpu.mov_eax(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("mov")) {
            VmCpu.mov(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("array_new")) {
            VmCpu.array_new(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("object_new")) {
            VmCpu.object_new(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("neg")) {
            VmCpu.neg(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("push_eax")) {
            VmCpu.push_eax(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("push_env")) {
            VmCpu.push_env(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("push_eip")) {
            VmCpu.push_eip(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("set_var")) {
            VmCpu.set_var(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("member_ref")) {
            VmCpu.member_ref(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("clear_call_stack")) {
            VmCpu.clear_call_stack(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("save_machine_state")) {
            VmCpu.save_machine_state(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("drop_machine_state")) {
            VmCpu.drop_machine_state(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("restore_machine_state")) {
            VmCpu.restore_machine_state(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("array_ref")) {
            VmCpu.array_ref(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("static_func")) {
            VmCpu.static_func(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("typeof")) {
            VmCpu.typeof(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("import_")) {
            VmCpu.import_(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("assign")) {
            VmCpu.assign(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("gt")) {
            VmCpu.gt(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("ge")) {
            VmCpu.ge(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("le")) {
            VmCpu.le(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("inc")) {
            VmCpu.inc(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("dec")) {
            VmCpu.dec(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("post_inc")) {
            VmCpu.post_inc(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("post_dec")) {
            VmCpu.post_dec(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("pop_eax")) {
            VmCpu.pop_eax(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("pop_env")) {
            VmCpu.pop_env(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("jmp")) {
            VmCpu.jmp(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("jz")) {
            VmCpu.jz(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("jnz")) {
            VmCpu.jnz(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("label")) {
            VmCpu.label(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("new_closure")) {
            VmCpu.new_closure(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("integer")) {
            VmCpu.integer(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("double_")) {
            VmCpu.double_(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("undefined")) {
            VmCpu.undefined(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("string")) {
            VmCpu.string(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("dequeue")) {
            VmCpu.dequeue(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("ret")) {
            VmCpu.ret(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("bit_and")) {
            VmCpu.bit_and(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("bit_or")) {
            VmCpu.bit_or(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("bit_xor")) {
            VmCpu.bit_xor(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("sub")) {
            VmCpu.sub(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("mul")) {
            VmCpu.mul(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("mod")) {
            VmCpu.mod(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("div")) {
            VmCpu.div(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("veq")) {
            VmCpu.veq(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("neq")) {
            VmCpu.neq(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("vneq")) {
            VmCpu.vneq(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("new_queue")) {
            VmCpu.new_queue(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("call")) {
            VmCpu.call(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("test")) {
            VmCpu.test(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("clear_null")) {
            VmCpu.clear_null(vm, instruction.param, instruction.param_extend);
            return true;
        }
        if (instruction.op.equals("new_op")) {
            VmCpu.new_op(vm, instruction.param, instruction.param_extend);
            return true;
        }
        return false;
    }
}

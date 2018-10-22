/* DO NOT WRITE ANYTHING MANUALLY */
package org.ngscript.vm.cpu;

import org.ngscript.common.Instruction;
import org.ngscript.vm.VmCpu;
import org.ngscript.vm.WscVM;
import org.ngscript.vm.WscVMException;

public class AutoCreatedCpuDispatcher {
    public static boolean dispatch(Instruction instruction, WscVM vm) throws WscVMException, Exception {
        switch (instruction.op) {
            case ADD:
                VmCpu.add(vm, instruction.param, instruction.paramExtended);
                return true;
            case CLEAR:
                VmCpu.clear(vm, instruction.param, instruction.paramExtended);
                return true;
            case LT:
                VmCpu.lt(vm, instruction.param, instruction.paramExtended);
                return true;
            case POP:
                VmCpu.pop(vm, instruction.param, instruction.paramExtended);
                return true;
            case PEEK:
                VmCpu.peek(vm, instruction.param, instruction.paramExtended);
                return true;
            case TEST:
                VmCpu.test(vm, instruction.param, instruction.paramExtended);
                return true;
            case EQ:
                VmCpu.eq(vm, instruction.param, instruction.paramExtended);
                return true;
            case DEREF:
                VmCpu.deref(vm, instruction.param, instruction.paramExtended);
                return true;
            case DEC:
                VmCpu.dec(vm, instruction.param, instruction.paramExtended);
                return true;
            case CALL:
                VmCpu.call(vm, instruction.param, instruction.paramExtended);
                return true;
            case CLEAR_CALL_STACK:
                VmCpu.clear_call_stack(vm, instruction.param, instruction.paramExtended);
                return true;
            case SUB:
                VmCpu.sub(vm, instruction.param, instruction.paramExtended);
                return true;
            case VEQ:
                VmCpu.veq(vm, instruction.param, instruction.paramExtended);
                return true;
            case NEQ:
                VmCpu.neq(vm, instruction.param, instruction.paramExtended);
                return true;
            case BIT_XOR:
                VmCpu.bit_xor(vm, instruction.param, instruction.paramExtended);
                return true;
            case BIT_AND:
                VmCpu.bit_and(vm, instruction.param, instruction.paramExtended);
                return true;
            case VNEQ:
                VmCpu.vneq(vm, instruction.param, instruction.paramExtended);
                return true;
            case DIV:
                VmCpu.div(vm, instruction.param, instruction.paramExtended);
                return true;
            case MUL:
                VmCpu.mul(vm, instruction.param, instruction.paramExtended);
                return true;
            case DEQUEUE:
                VmCpu.dequeue(vm, instruction.param, instruction.paramExtended);
                return true;
            case NEW_OP:
                VmCpu.new_op(vm, instruction.param, instruction.paramExtended);
                return true;
            case NEW_QUEUE:
                VmCpu.new_queue(vm, instruction.param, instruction.paramExtended);
                return true;
            case BIT_OR:
                VmCpu.bit_or(vm, instruction.param, instruction.paramExtended);
                return true;
            case RET:
                VmCpu.ret(vm, instruction.param, instruction.paramExtended);
                return true;
            case UNDEFINED:
                VmCpu.undefined(vm, instruction.param, instruction.paramExtended);
                return true;
            case MOD:
                VmCpu.mod(vm, instruction.param, instruction.paramExtended);
                return true;
            case CLEAR_NULL:
                VmCpu.clear_null(vm, instruction.param, instruction.paramExtended);
                return true;
            case TYPEOF:
                VmCpu.typeof(vm, instruction.param, instruction.paramExtended);
                return true;
            case INC:
                VmCpu.inc(vm, instruction.param, instruction.paramExtended);
                return true;
            case POP_EAX:
                VmCpu.pop_eax(vm, instruction.param, instruction.paramExtended);
                return true;
            case ASSIGN:
                VmCpu.assign(vm, instruction.param, instruction.paramExtended);
                return true;
            case JNZ:
                VmCpu.jnz(vm, instruction.param, instruction.paramExtended);
                return true;
            case LABEL:
                VmCpu.label(vm, instruction.param, instruction.paramExtended);
                return true;
            case NEW_CLOSURE:
                VmCpu.new_closure(vm, instruction.param, instruction.paramExtended);
                return true;
            case PUSH_ENV:
                VmCpu.push_env(vm, instruction.param, instruction.paramExtended);
                return true;
            case SET_VAR:
                VmCpu.set_var(vm, instruction.param, instruction.paramExtended);
                return true;
            case POST_INC:
                VmCpu.post_inc(vm, instruction.param, instruction.paramExtended);
                return true;
            case MOV_EAX:
                VmCpu.mov_eax(vm, instruction.param, instruction.paramExtended);
                return true;
            case OBJECT_NEW:
                VmCpu.object_new(vm, instruction.param, instruction.paramExtended);
                return true;
            case GE:
                VmCpu.ge(vm, instruction.param, instruction.paramExtended);
                return true;
            case POP_ENV:
                VmCpu.pop_env(vm, instruction.param, instruction.paramExtended);
                return true;
            case JZ:
                VmCpu.jz(vm, instruction.param, instruction.paramExtended);
                return true;
            case POST_DEC:
                VmCpu.post_dec(vm, instruction.param, instruction.paramExtended);
                return true;
            case JMP:
                VmCpu.jmp(vm, instruction.param, instruction.paramExtended);
                return true;
            case MOV:
                VmCpu.mov(vm, instruction.param, instruction.paramExtended);
                return true;
            case ARRAY_NEW:
                VmCpu.array_new(vm, instruction.param, instruction.paramExtended);
                return true;
            case PUSH_EAX:
                VmCpu.push_eax(vm, instruction.param, instruction.paramExtended);
                return true;
            case PUSH_EIP:
                VmCpu.push_eip(vm, instruction.param, instruction.paramExtended);
                return true;
            case MEMBER_REF:
                VmCpu.member_ref(vm, instruction.param, instruction.paramExtended);
                return true;
            case NEG:
                VmCpu.neg(vm, instruction.param, instruction.paramExtended);
                return true;
            case STATIC_FUNC:
                VmCpu.static_func(vm, instruction.param, instruction.paramExtended);
                return true;
            case IMPORT_:
                VmCpu.import_(vm, instruction.param, instruction.paramExtended);
                return true;
            case ARRAY_REF:
                VmCpu.array_ref(vm, instruction.param, instruction.paramExtended);
                return true;
            case GT:
                VmCpu.gt(vm, instruction.param, instruction.paramExtended);
                return true;
            case LE:
                VmCpu.le(vm, instruction.param, instruction.paramExtended);
                return true;
            case DOUBLE_:
                VmCpu.double_(vm, instruction.param, instruction.paramExtended);
                return true;
            case INTEGER:
                VmCpu.integer(vm, instruction.param, instruction.paramExtended);
                return true;
            case MOV_EXCEPTION_EAX:
                VmCpu.mov_exception_eax(vm, instruction.param, instruction.paramExtended);
                return true;
            case MOV_EAX_EXCEPTION:
                VmCpu.mov_eax_exception(vm, instruction.param, instruction.paramExtended);
                return true;
            case STRING:
                VmCpu.string(vm, instruction.param, instruction.paramExtended);
                return true;
            case RESTORE_MACHINE_STATE:
                VmCpu.restore_machine_state(vm, instruction.param, instruction.paramExtended);
                return true;
            case SAVE_MACHINE_STATE:
                VmCpu.save_machine_state(vm, instruction.param, instruction.paramExtended);
                return true;
            case DROP_MACHINE_STATE:
                VmCpu.drop_machine_state(vm, instruction.param, instruction.paramExtended);
                return true;
        }
        return false;
    }
}
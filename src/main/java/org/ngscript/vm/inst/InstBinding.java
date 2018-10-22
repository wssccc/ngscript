package org.ngscript.vm.inst;

import org.ngscript.common.Instruction;
import org.ngscript.vm.InvokableInstruction;
import org.ngscript.vm.WscVM;
import org.ngscript.vm.WscVMException;

public class InstBinding extends Instruction {

    InvokableInstruction invokableInstruction;

    public InstBinding(Instruction instruction, InvokableInstruction invokableInstruction) {
        super(instruction.op, instruction.param, instruction.paramExtended);
        this.invokableInstruction = invokableInstruction;
    }

    public void invoke(WscVM vm) throws WscVMException {
        invokableInstruction.invoke(vm, param, paramExtended);
    }
}

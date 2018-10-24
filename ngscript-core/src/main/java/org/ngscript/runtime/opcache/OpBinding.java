package org.ngscript.runtime.opcache;

import org.ngscript.compiler.Instruction;
import org.ngscript.runtime.InvokableInstruction;
import org.ngscript.runtime.VirtualMachine;
import org.ngscript.runtime.VmRuntimeException;

public class OpBinding extends Instruction {

    InvokableInstruction invokableInstruction;

    public OpBinding(Instruction instruction, InvokableInstruction invokableInstruction) {
        super(instruction.op, instruction.param, instruction.paramExtended);
        this.invokableInstruction = invokableInstruction;
    }

    public void invoke(VirtualMachine vm) throws VmRuntimeException {
        invokableInstruction.invoke(vm, param, paramExtended);
    }
}

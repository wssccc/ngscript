package org.ngscript.vm;

public interface InvokableInstruction {

    void invoke(WscVM vm, String param, String param_extend) throws WscVMException;
}

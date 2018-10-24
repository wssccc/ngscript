package org.ngscript.runtime;

public interface InvokableInstruction {

    void invoke(VirtualMachine vm, String param, String paramEx) throws VmRuntimeException;
}

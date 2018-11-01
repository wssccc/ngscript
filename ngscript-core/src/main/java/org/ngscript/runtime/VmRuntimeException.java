/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class VmRuntimeException extends RuntimeException {

    VirtualMachine vm;

    public VmRuntimeException(String message) {
        super(message);
    }

    public VmRuntimeException(Throwable cause) {
        super(cause);
    }

    public VmRuntimeException(VirtualMachine vm, String message) {
        super(genInfoString(vm, message));
        this.vm = vm;
    }

    final static String genInfoString(VirtualMachine vm, String message) {
        try {
            StringBuilder sb = new StringBuilder();
            sb
                    .append("\r\n========== VM ERROR ==========\r\n")
                    .append(message)
                    .append("\r\nnear code line ").append(vm.helptext.paramExtended).append("\r\n")
                    .append(vm.helptext.toString())
                    .append("\r\n========== VM STATUS ==========\r\n")
                    .append("%eip = ").append(vm.eip).append("\r\n")
                    .append("ins = ").append(vm.instructions[vm.eip - 1]).append("\r\n")
                    .append("%env = ").append((vm.env.read()).toString()).append("\r\n")
                    .append("%eax = ").append(vm.eax.read() == null ? "null" : vm.eax.read().toString()).append("\r\n")
                    .append("==============================\r\n");
            return sb.toString();
        } catch (Exception ex) {
            return message;
        }
    }

}

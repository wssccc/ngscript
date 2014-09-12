/*
 *  wssccc all rights reserved
 */
package ngscript.vm;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscVMException extends Exception {

    WscVM vm;

    public WscVMException(WscVM vm, String message) {
        super(genInfoString(vm, message));
        this.vm = vm;
    }

    final static String genInfoString(WscVM vm, String message) {
        StringBuilder sb = new StringBuilder();
        sb
                .append("\r\n========== VM ERROR ==========\r\n")
                .append(message)
                .append("\r\nnear code line ").append(vm.helptext.param_extend).append("\r\n")
                .append(vm.helptext.toString())
                .append("\r\n========== VM STATUS ==========\r\n")
                .append("%eip = ").append(vm.eip).append("\r\n")
                .append("ins = ").append(vm.instructions.get(vm.eip - 1)).append("\r\n")
                .append("%env = ").append(((ScopeHash) vm.env.read()).toString()).append("\r\n")
                .append("%eax = ").append(vm.eax.read() == null ? "null" : vm.eax.read().toString()).append("\r\n")
                .append("==============================\r\n");
        return sb.toString();
    }

}

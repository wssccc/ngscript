/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.runtime;

/**
 * @author wssccc
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
                    .append("\r\nnear code line ").append(vm.hints.paramExtended).append("\r\n")
                    .append(vm.hints.toString())
                    .append("\r\n========== VM STATUS ==========\r\n")
                    .append("%eip = ").append(vm.getEip()).append("\r\n")
                    .append("ins = ").append(vm.instructions[vm.getEip() - 1]).append("\r\n")
                    .append("%env = ").append((vm.env.read()).toString()).append("\r\n")
                    .append("%eax = ").append(vm.eax.read() == null ? "null" : vm.eax.read().toString()).append("\r\n")
                    .append("==============================\r\n");
            return sb.toString();
        } catch (Exception ex) {
            return message;
        }
    }

}

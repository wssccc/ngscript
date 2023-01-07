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

    static String genInfoString(VirtualMachine vm, String message) {
        try {
            return "\r\n========== VM ERROR ==========\r\n" +
                    message +
                    "\r\nnear code line " + vm.hints.paramExt + "\r\n" +
                    vm.hints +
                    "\r\n========== VM STATUS ==========\r\n" +
                    "%eip = " + vm.getEip() + "\r\n" +
                    "ins = " + vm.instructions[vm.getEip() - 1] + "\r\n" +
                    "%env = " + (vm.env.read()).toString() + "\r\n" +
                    "%eax = " + (vm.eax.read() == null ? "null" : vm.eax.read().toString()) + "\r\n" +
                    "==============================\r\n";
        } catch (Exception ex) {
            return message;
        }
    }

}

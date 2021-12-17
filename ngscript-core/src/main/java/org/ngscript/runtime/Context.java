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

import org.ngscript.compiler.Instruction;
import org.ngscript.utils.FastStack;

/**
 * @author wssccc
 */
public class Context {

    Object eax;
    Environment env;
    int eip;
    Instruction hint;
    int stackSize;
    int callStackSize;
    FastStack<Object> stack;

    public Context(VirtualMachine vm) {
        this.save(vm);
    }

    public final void save(VirtualMachine vm) {
        this.eax = vm.eax.read();
        this.env = (Environment) vm.env.read();
        this.eip = vm.getEip();
        this.hint = vm.helptext;
        this.stackSize = vm.stack.size();
        this.callStackSize = vm.callstack.size();
        this.stack = vm.stack;
    }

    public final void restore(VirtualMachine vm) {
        vm.eax.write(eax);
        vm.env.write(env);
        vm.setEip(eip);
        vm.helptext = hint;
        vm.stack = stack;
        //the following while loop is to ensure the stack is balanced when in a try catch block
        while (vm.stack.size() > stackSize) {
            vm.stack.pop();
        }
        vm.call_stack_size = callStackSize;
    }
}

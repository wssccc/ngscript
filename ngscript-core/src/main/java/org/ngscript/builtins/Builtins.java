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

package org.ngscript.builtins;

import java.io.PrintWriter;
import java.util.Map;

import org.ngscript.runtime.Coroutine;
import org.ngscript.runtime.Environment;
import org.ngscript.runtime.OpUtils;
import org.ngscript.runtime.VirtualMachine;
import org.ngscript.runtime.VmRuntimeException;
import org.ngscript.runtime.value.VmInvokable;
import org.ngscript.runtime.value.VmMemRef;

/**
 * Registry of ngscript built-in functions and objects.
 *
 * @author wssccc
 */
public class Builtins {

    public static void register(Map<String, VmMemRef> map, PrintWriter out) {
        map.put("println", new VmMemRef(new PrintLn(out)));
        map.put("print", new VmMemRef(new Print(out)));
        map.put("assert", new VmMemRef(new Assert()));
        map.put("Object", new VmMemRef(new CreateEnv()));
        map.put("Coroutine", new VmMemRef(Coroutine.class));
    }

    static class PrintLn implements VmInvokable {
        private final PrintWriter out;

        PrintLn(PrintWriter out) {
            this.out = out;
        }

        @Override
        public void invoke(VirtualMachine vm, Object[] args) {
            for (Object var : args) {
                out.print(var);
            }
            out.println();
            out.flush();
        }
    }

    static class Print implements VmInvokable {
        private final PrintWriter out;

        Print(PrintWriter out) {
            this.out = out;
        }

        @Override
        public void invoke(VirtualMachine vm, Object[] args) {
            for (Object var : args) {
                out.print(var);
            }
            out.flush();
        }
    }

    static class Assert implements VmInvokable {
        @Override
        public void invoke(VirtualMachine vm, Object[] args) {
            for (Object var : args) {
                if (!OpUtils.testValue(var)) {
                    throw new VmRuntimeException("Assertion failed");
                }
            }
        }
    }

    static class CreateEnv implements VmInvokable {
        @Override
        public void invoke(VirtualMachine vm, Object[] args) {
            //prepare env
            Environment env = new Environment((Environment) vm.env.read());
            vm.env.write(env);
        }
    }
}

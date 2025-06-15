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

import org.ngscript.runtime.VirtualMachine;
import org.ngscript.value.VmInvokable;
import org.ngscript.value.VmMemRef;

/**
 * Built-in console functions: {@code println} and {@code print}.
 *
 * @author wssccc
 */
public class ConsoleBuiltins {

    static void register(Map<String, VmMemRef> map, PrintWriter out) {
        map.put("println", new VmMemRef(new PrintLn(out)));
        map.put("print", new VmMemRef(new Print(out)));
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
}

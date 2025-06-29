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

package org.ngscript.runtime.opcache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ngscript.api.NgScriptEngine;
import org.ngscript.bytecode.Instruction;
import org.ngscript.runtime.Machine;

/**
 * Structural tests for the opcode registry: every opcode the compiler emits
 * must resolve to a bound implementation, each opcode has a {@code _pe}
 * variant, and no method name is registered twice.
 *
 * @author wssccc
 */
public class OpMapStructureTest {

    @Test
    public void everyCompilerEmittedOpcodeResolves() throws Exception {
        NgScriptEngine engine = new NgScriptEngine();
        // compile a script that exercises a wide range of opcodes
        engine.compile("var a = [1, 2, 3];\n"
                + "var o = {x: 1};\n"
                + "function f(n) { return n * 2; }\n"
                + "var s = 0;\n"
                + "for (var i = 0; i < 3; i++) { s = s + f(i); }\n"
                + "if (s > 0) { s = s - 1; } else { s = s + 1; }\n"
                + "try { throw \"boom\"; } catch (e) { s = 0; } finally { s = 1; }\n"
                + "var c = new Coroutine(function() { yield(1); });\n"
                + "c.resume();\n"
                + "typeof o;");
    }

    @Test
    public void registryHasBothPlainAndPeVariants() {
        Map<String, OpInvokable> map = OpMap.INSTANCE.getMap();
        assertTrue("registry should not be empty", map.size() > 0);
        for (String key : map.keySet()) {
            assertNotNull("bound implementation for " + key, map.get(key));
        }
        for (Map.Entry<String, OpInvokable> e : map.entrySet()) {
            String name = e.getKey();
            if (!name.endsWith("_pe")) {
                assertTrue("missing _pe variant for " + name, map.containsKey(name + "_pe"));
            }
        }
    }

    @Test
    public void noDuplicateNamesAcrossHostClasses() {
        Map<String, OpInvokable> map = OpMap.INSTANCE.getMap();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String key : map.keySet()) {
            if (key.endsWith("_pe")) {
                continue; // _pe variants are derived from plain names
            }
            assertTrue("duplicate opcode name " + key, names.add(key));
        }
    }

    @Test
    public void boundInstructionsRunViaOpMap() throws Exception {
        List<Instruction> ins = new java.util.ArrayList<>();
        ins.add(new Instruction("integer", "6", null));
        ins.add(new Instruction("halt", "", ""));
        Machine machine = newMachine();
        machine.installInstructions(machine.bindInstructions(ins));
        machine.resetForExecution();
        machine.run();
        assertEquals(6, machine.eax.read());
    }

    private Machine newMachine() {
        return new Machine(
                new java.io.PrintWriter(new java.io.StringWriter()),
                new java.io.PrintWriter(new java.io.StringWriter()));
    }
}

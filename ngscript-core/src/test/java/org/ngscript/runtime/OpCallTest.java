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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ngscript.bytecode.Instruction;
import org.ngscript.value.ScriptFunction;

/**
 * Instruction-level tests for the call-related opcodes.
 *
 * @author wssccc
 */
public class OpCallTest extends OpTestBase {

    @Test
    public void newClosureCapturesEnvironment() {
        runOp("new_closure", "L1", null);
        ScriptFunction closure = (ScriptFunction) vm.eax.read();
        assertEquals("L1", closure.functionLabel);
        assertSame(vm.env.read(), closure.environment);
    }

    @Test
    public void importRecordsShortName() {
        runOp("import_", "java.util.ArrayList", null);
        assertEquals("java.util.ArrayList", vm.imported.get("ArrayList"));
    }

    @Test
    public void callScriptFunction() {
        ScriptFunction fn = new ScriptFunction("L10", (Environment) vm.env.read(), vm);
        Object[] args = new Object[0];
        runOps(() -> {
                    // stack order (bottom first): args, callee, caller env
                    vm.stack.push(args);
                    vm.stack.push(fn);
                    vm.stack.push(new Environment(null));
                },
                new Instruction("call", "", null),
                new Instruction("halt", "", ""),
                new Instruction("label", "L10", null),
                new Instruction("integer", "42", null),
                new Instruction("ret", null, null));
        assertEquals(42, vm.eax.read());
        assertTrue(vm.callstack.isEmpty());
    }

    @Test
    public void saveMachineStatePushesContext() {
        runOp("save_machine_state", null, null);
        assertEquals(1, vm.machineStateStack.size());
    }

    @Test
    public void dropMachineStatePopsContext() {
        runOps(
                new Instruction("save_machine_state", null, null),
                new Instruction("drop_machine_state", null, null),
                new Instruction("halt", "", ""));
        assertTrue(vm.machineStateStack.isEmpty());
    }

    @Test
    public void restoreMachineStateRestoresRegisters() {
        runOps(
                new Instruction("integer", "5", null),
                new Instruction("halt", "", ""));
        Context saved = new Context(vm);
        vm.machineStateStack.push(saved);
        vm.eax.write(99);
        runOp("restore_machine_state", null, null);
        assertEquals(5, vm.eax.read());
        assertTrue(vm.machineStateStack.isEmpty());
    }
}

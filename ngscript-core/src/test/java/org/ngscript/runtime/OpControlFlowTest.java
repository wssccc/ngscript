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
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ngscript.bytecode.Instruction;
import org.ngscript.value.ScriptFunction;

/**
 * Instruction-level tests for the control-flow opcodes.
 *
 * @author wssccc
 */
public class OpControlFlowTest extends OpTestBase {

    @Test
    public void jmpToLabel() {
        runOps(
                new Instruction("jmp", "L0", null),
                new Instruction("integer", "1", null),
                new Instruction("label", "L0", null),
                new Instruction("integer", "42", null),
                new Instruction("halt", "", ""));
        assertEquals(42, vm.eax.read());
    }

    @Test
    public void jzBranchesOnZero() {
        runOps(
                new Instruction("integer", "0", null),
                new Instruction("jz", "L0", null),
                new Instruction("integer", "1", null),
                new Instruction("label", "L0", null),
                new Instruction("integer", "42", null),
                new Instruction("halt", "", ""));
        assertEquals(42, vm.eax.read());
    }

    @Test
    public void jzFallsThroughOnNonZero() {
        runOps(
                new Instruction("integer", "5", null),
                new Instruction("jz", "L0", null),
                new Instruction("integer", "42", null),
                new Instruction("label", "L0", null),
                new Instruction("halt", "", ""));
        assertEquals(42, vm.eax.read());
    }

    @Test
    public void jnzBranchesOnNonZero() {
        runOps(
                new Instruction("integer", "5", null),
                new Instruction("jnz", "L0", null),
                new Instruction("integer", "1", null),
                new Instruction("label", "L0", null),
                new Instruction("integer", "42", null),
                new Instruction("halt", "", ""));
        assertEquals(42, vm.eax.read());
    }

    @Test
    public void labelIsNoOp() {
        runOp("label", "L0", null);
    }

    @Test
    public void goRunsBodyAndResumes() {
        runOps(
                new Instruction("go", "L0", null),
                new Instruction("integer", "7", null),
                new Instruction("label", "L0", null),
                new Instruction("integer", "5", null),
                new Instruction("halt", "", ""));
        assertEquals(5, vm.eax.read());
    }

    @Test
    public void haltStopsExecution() {
        runOps(
                new Instruction("integer", "3", null),
                new Instruction("halt", "", ""));
        assertEquals(3, vm.eax.read());
    }

    @Test
    public void retPopsReturnAddressAndFrame() {
        ScriptFunction frame = new ScriptFunction("L1", (NgsObject) vm.env.read(), vm);
        runOps(() -> {
                    vm.callstack.push(frame);
                    // return address: the integer 42 instruction (index 1)
                    vm.stack.push(1);
                },
                new Instruction("ret", null, null),
                new Instruction("integer", "42", null),
                new Instruction("halt", "", ""));
        assertEquals(42, vm.eax.read());
        assertTrue(vm.callstack.isEmpty());
    }

    @Test
    public void clearCallStackTruncatesToSavedSize() {
        ScriptFunction f1 = new ScriptFunction("L1", (NgsObject) vm.env.read(), vm);
        ScriptFunction f2 = new ScriptFunction("L2", (NgsObject) vm.env.read(), vm);
        runOps(() -> {
                    vm.callstack.push(f1);
                    vm.callstack.push(f2);
                    vm.callStackSize = 1;
                },
                new Instruction("clear_call_stack", null, null),
                new Instruction("halt", "", ""));
        assertEquals(1, vm.callstack.size());
    }
}

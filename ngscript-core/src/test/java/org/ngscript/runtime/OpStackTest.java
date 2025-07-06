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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.ngscript.bytecode.Instruction;
import org.ngscript.value.Undefined;

/**
 * Instruction-level tests for the stack opcodes.
 *
 * @author wssccc
 */
public class OpStackTest extends OpTestBase {

    @Test
    public void pushEax() {
        runOps(new Instruction("integer", "5", null),
                new Instruction("push_eax", null, null),
                new Instruction("halt", "", ""));
        assertEquals(5, vm.stack.peek(0));
    }

    @Test
    public void pushEnv() {
        runOp("push_env", null, null);
        assertSame(vm.env.read(), vm.stack.peek(0));
    }

    @Test
    public void pushEip() {
        runOp("push_eip", null, null);
        // eip was 1 when push_eip executed (incremented before invoke)
        assertEquals(1, vm.stack.peek(0));
    }

    @Test
    public void popEax() {
        runOps(() -> vm.stack.push(5),
                new Instruction("pop_eax", null, null),
                new Instruction("halt", "", ""));
        assertEquals(5, vm.eax.read());
    }

    @Test
    public void popEnv() {
        NgsObject other = new NgsObject(null);
        runOps(() -> vm.stack.push(other),
                new Instruction("pop_env", null, null),
                new Instruction("halt", "", ""));
        assertSame(other, vm.env.read());
    }

    @Test
    public void pop() {
        runOps(() -> vm.stack.push(1),
                new Instruction("pop", null, null),
                new Instruction("halt", "", ""));
        assertEquals(0, vm.stack.size());
    }

    @Test
    public void peekTop() {
        runOps(() -> {
                    vm.stack.push(5);
                    vm.stack.push(6);
                },
                new Instruction("peek", "0", null),
                new Instruction("halt", "", ""));
        assertEquals(6, vm.eax.read());
    }

    @Test
    public void pickargByOffset() {
        Object[] args = new Object[]{"a", "b", "c"};
        runOps(() -> {
                    vm.stack.push(args);
                    vm.stack.push(1);
                    vm.stack.push(2);
                    vm.stack.push(3);
                },
                new Instruction("pickarg", "1", null),
                new Instruction("halt", "", ""));
        assertEquals("b", vm.eax.read());
    }

    @Test
    public void pickargOutOfRangeIsUndefined() {
        Object[] args = new Object[]{"a"};
        runOps(() -> {
                    vm.stack.push(args);
                    vm.stack.push(1);
                    vm.stack.push(2);
                    vm.stack.push(3);
                },
                new Instruction("pickarg", "5", null),
                new Instruction("halt", "", ""));
        assertEquals(Undefined.value, vm.eax.read());
    }

    @Test
    public void packargs() {
        runOps(() -> {
                    vm.stack.push(1);
                    vm.stack.push(2);
                },
                new Instruction("packargs", "2", null),
                new Instruction("halt", "", ""));
        assertArrayEquals(new Object[]{2, 1}, (Object[]) vm.eax.read());
    }
}

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

import org.junit.Test;
import org.ngscript.bytecode.Instruction;
import org.ngscript.value.MemRef;

/**
 * Instruction-level tests for the move opcodes.
 *
 * @author wssccc
 */
public class OpMoveTest extends OpTestBase {

    @Test
    public void derefReadsValue() {
        runOps(() -> frame().set("x", 5),
                new Instruction("deref", "x", null),
                new Instruction("halt", "", ""));
        assertEquals(5, vm.eax.read());
    }

    @Test
    public void derefEaxUnwrapsMemRef() {
        runOps(() -> frame().set("x", 5),
                new Instruction("mov_eax", "x", null),
                new Instruction("deref", "%eax", null),
                new Instruction("halt", "", ""));
        assertEquals(5, vm.eax.read());
    }

    @Test
    public void movEaxWritesReference() {
        runOps(() -> frame().set("x", 5),
                new Instruction("mov_eax", "x", null),
                new Instruction("halt", "", ""));
        MemRef ref = (MemRef) vm.eax.read();
        assertEquals(5, ref.read());
    }

    @Test
    public void movExceptionEax() {
        runOps(new Instruction("integer", "7", null),
                new Instruction("mov_exception_eax", null, null),
                new Instruction("halt", "", ""));
        assertEquals(7, vm.exception.read());
    }

    @Test
    public void movEaxException() {
        runOps(new Instruction("integer", "9", null),
                new Instruction("mov_exception_eax", null, null),
                new Instruction("mov_eax_exception", null, null),
                new Instruction("halt", "", ""));
        assertEquals(9, vm.eax.read());
    }

    @Test
    public void movValueCopy() {
        runOps(() -> {
                    frame().set("x", 1);
                    frame().set("y", 2);
                },
                new Instruction("mov", "x", "[y]"),
                new Instruction("halt", "", ""));
        assertEquals(2, frame().get("x"));
    }

    @Test
    public void movRefCopySharesReference() {
        runOps(() -> {
                    frame().set("x", 1);
                    frame().set("y", 2);
                },
                new Instruction("mov", "x", "y"),
                new Instruction("halt", "", ""));
        // the ref copy writes the source MemRef object into the target reference
        assertSame(frame().getRef("y"), frame().getRef("x").read());
    }

    @Test
    public void movFromEaxRegister() {
        runOps(() -> frame().set("x", 1),
                new Instruction("integer", "42", null),
                new Instruction("mov", "x", "%eax"),
                new Instruction("halt", "", ""));
        assertEquals(42, frame().get("x"));
    }

    private NgsObject frame() {
        return (NgsObject) vm.env.read();
    }
}

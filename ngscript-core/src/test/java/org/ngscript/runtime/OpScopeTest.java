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
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.ngscript.bytecode.Instruction;
import org.ngscript.value.MemRef;

/**
 * Instruction-level tests for the scope-write opcodes.
 *
 * @author wssccc
 */
public class OpScopeTest extends OpTestBase {

    @Test
    public void setWritesEaxIntoCurrentFrame() {
        runOps(new Instruction("integer", "5", null),
                new Instruction("set", "x", null),
                new Instruction("halt", "", ""));
        assertEquals(5, frame().get("x"));
    }

    @Test
    public void valCreatesNullReference() {
        runOp("val", "x", null);
        assertNull(frame().get("x"));
    }

    @Test
    public void assignWritesThroughReference() {
        MemRef target = new MemRef(1);
        runOps(() -> vm.stack.push(target),
                new Instruction("integer", "7", null),
                new Instruction("assign", null, null),
                new Instruction("halt", "", ""));
        assertEquals(7, target.read());
        assertEquals(7, vm.eax.read());
    }

    private NgsObject frame() {
        return (NgsObject) vm.env.read();
    }
}

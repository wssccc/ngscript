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

import org.junit.Test;
import org.ngscript.bytecode.Instruction;
import org.ngscript.value.MemRef;

/**
 * Instruction-level tests for the array opcodes.
 *
 * @author wssccc
 */
public class OpArrayTest extends OpTestBase {

    @Test
    public void arrayNewPopsElementsInOrder() {
        // compiler pushes elements in reverse order, so the first element
        // ends up on top and is popped first
        runOps(() -> {
                    vm.stack.push(3);
                    vm.stack.push(2);
                    vm.stack.push(1);
                },
                new Instruction("array_new", "3", null),
                new Instruction("halt", "", ""));
        Environment array = (Environment) vm.eax.read();
        assertEquals(1, array.data.get("0").read());
        assertEquals(2, array.data.get("1").read());
        assertEquals(3, array.data.get("2").read());
    }

    @Test
    public void arrayRefByIndex() {
        Environment array = new Environment(null);
        array.data.put("0", new MemRef(10));
        array.data.put("1", new MemRef(20));
        runOps(() -> vm.stack.push(array),
                new Instruction("integer", "1", null),
                new Instruction("array_ref", null, null),
                new Instruction("halt", "", ""));
        MemRef ref = (MemRef) vm.eax.read();
        assertEquals(20, ref.read());
    }

    @Test
    public void arrayRefOnJavaList() {
        java.util.ArrayList<MemRef> list = new java.util.ArrayList<>();
        list.add(new MemRef(7));
        runOps(() -> vm.stack.push(list),
                new Instruction("integer", "0", null),
                new Instruction("array_ref", null, null),
                new Instruction("halt", "", ""));
        MemRef ref = (MemRef) vm.eax.read();
        assertEquals(7, ref.read());
    }
}

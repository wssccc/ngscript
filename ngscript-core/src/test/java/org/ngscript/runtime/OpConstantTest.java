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
import org.ngscript.value.Undefined;

/**
 * Instruction-level tests for the constant opcodes.
 *
 * @author wssccc
 */
public class OpConstantTest extends OpTestBase {

    @Test
    public void integer() {
        runOp("integer", "42", null);
        assertEquals(42, vm.eax.read());
    }

    @Test
    public void boolTrue() {
        runOp("bool", "true", null);
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void boolFalse() {
        runOp("bool", "false", null);
        assertEquals(Boolean.FALSE, vm.eax.read());
    }

    @Test
    public void doubleValue() {
        runOp("double_", "3.14", null);
        assertEquals(3.14, vm.eax.read());
    }

    @Test
    public void undefinedValue() {
        runOp("undefined", null, null);
        assertSame(Undefined.value, vm.eax.read());
    }

    @Test
    public void stringUnquoted() {
        runOp("string", "hello", null);
        assertEquals("hello", vm.eax.read());
    }

    @Test
    public void stringQuotedStripsQuotes() {
        runOp("string", "\"hello\"", null);
        assertEquals("hello", vm.eax.read());
    }

    @Test
    public void stringEscapesNewline() {
        runOp("string", "a\\nb", null);
        assertEquals("a\nb", vm.eax.read());
    }
}

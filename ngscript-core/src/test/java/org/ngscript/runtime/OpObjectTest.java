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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ngscript.bytecode.Instruction;
import org.ngscript.value.MemRef;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * Instruction-level tests for the object opcodes.
 *
 * @author wssccc
 */
public class OpObjectTest extends OpTestBase {

    @Test
    public void objectNew() {
        runOps(() -> {
                    vm.stack.push("a");
                    vm.stack.push(1);
                    vm.stack.push("b");
                    vm.stack.push(2);
                },
                new Instruction("object_new", "2", null),
                new Instruction("halt", "", ""));
        NgsObject obj = (NgsObject) vm.eax.read();
        assertEquals(1, obj.get("a"));
        assertEquals(2, obj.get("b"));
    }

    @Test
    public void memberRefOnEnvironment() {
        NgsObject env = new NgsObject(null);
        env.set("x", 9);
        runOps(() -> vm.stack.push(env),
                new Instruction("string", "x", null),
                new Instruction("member_ref", null, null),
                new Instruction("halt", "", ""));
        MemRef ref = (MemRef) vm.eax.read();
        assertEquals(9, ref.read());
    }

    @Test
    public void staticFuncRegistersClosure() {
        runOp("static_func", "f", "L1");
        MemRef ref = frame().lookup("f", vm, false);
        assertTrue(ref.read() instanceof ScriptFunction);
    }

    @Test
    public void typeofNumber() {
        runOps(new Instruction("integer", "1", null), new Instruction("typeof", null, null),
                new Instruction("halt", "", ""));
        assertEquals("number", vm.eax.read());
    }

    @Test
    public void typeofUndefined() {
        runOps(new Instruction("undefined", null, null), new Instruction("typeof", null, null),
                new Instruction("halt", "", ""));
        assertEquals("undefined", vm.eax.read());
    }

    @Test
    public void typeofString() {
        runOps(new Instruction("string", "abc", null), new Instruction("typeof", null, null),
                new Instruction("halt", "", ""));
        assertEquals("string", vm.eax.read());
    }

    @Test
    public void testDefinedVariable() {
        runOps(() -> frame().set("x", 1),
                new Instruction("test", "x", null),
                new Instruction("halt", "", ""));
        assertEquals(1, vm.eax.read());
    }

    @Test
    public void clearSetsUndefined() {
        runOps(() -> frame().set("x", 1),
                new Instruction("clear", "x", null),
                new Instruction("halt", "", ""));
        assertEquals(Undefined.value, frame().get("x"));
    }

    @Test
    public void clearNullSetsNull() {
        runOps(() -> frame().set("x", 1),
                new Instruction("clear_null", "x", null),
                new Instruction("halt", "", ""));
        assertNull(frame().get("x"));
    }

    @Test
    public void newOpScriptConstructorReturnsActiveFrame() {
        NgsObject instance = new NgsObject(null);
        NgsObject oldEnv = new NgsObject(null);
        runOps(() -> {
                    vm.env.write(instance);
                    // stack order (top first): params, constructor, caller env
                    vm.stack.push("params");
                    vm.stack.push("the-constructor");
                    vm.stack.push(oldEnv);
                },
                new Instruction("new_op", null, null),
                new Instruction("halt", "", ""));
        assertSame(instance, vm.eax.read());
        assertSame(oldEnv, vm.env.read());
    }

    @Test
    public void newOpNativeConstructorPopsInstance() {
        NgsObject oldEnv = new NgsObject(null);
        Object nativeInstance = new Object();
        runOps(() -> {
                    vm.env.write(null);
                    // stack order (bottom first): params, constructor, caller env, instance
                    vm.stack.push("params");
                    vm.stack.push("the-constructor");
                    vm.stack.push(oldEnv);
                    vm.stack.push(nativeInstance);
                },
                new Instruction("new_op", null, null),
                new Instruction("halt", "", ""));
        assertSame(nativeInstance, vm.eax.read());
        assertSame(oldEnv, vm.env.read());
    }

    private NgsObject frame() {
        return (NgsObject) vm.env.read();
    }
}

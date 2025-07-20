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

/**
 * Instruction-level tests for the arithmetic opcodes.
 *
 * @author wssccc
 */
public class OpArithmeticTest extends OpTestBase {

    /** eax = left, push left, eax = right (as the compiler emits for binary ops). */
    private void binary(String leftOp, String leftParam, String rightOp, String rightParam, String op) {
        runOps(
                new Instruction(leftOp, leftParam, null),
                new Instruction("push_eax", null, null),
                new Instruction(rightOp, rightParam, null),
                new Instruction(op, null, null),
                new Instruction("halt", "", ""));
    }

    @Test
    public void negInteger() {
        runOps(new Instruction("integer", "5", null), new Instruction("neg", null, null),
                new Instruction("halt", "", ""));
        assertEquals(-5, vm.eax.read());
    }

    @Test
    public void negDouble() {
        runOps(new Instruction("double_", "5.5", null), new Instruction("neg", null, null),
                new Instruction("halt", "", ""));
        assertEquals(-5.5, vm.eax.read());
    }

    @Test
    public void add() {
        binary("integer", "3", "integer", "4", "add");
        assertEquals(7, vm.eax.read());
    }

    @Test
    public void sub() {
        binary("integer", "10", "integer", "3", "sub");
        assertEquals(7, vm.eax.read());
    }

    @Test
    public void mul() {
        binary("integer", "3", "integer", "4", "mul");
        assertEquals(12, vm.eax.read());
    }

    @Test
    public void mod() {
        binary("integer", "10", "integer", "3", "mod");
        assertEquals(1, vm.eax.read());
    }

    @Test
    public void divInt() {
        // ES division always has double semantics: 10 / 4 == 2.5
        binary("integer", "10", "integer", "4", "div");
        assertEquals(2.5, vm.eax.read());
    }

    @Test
    public void divByZero() {
        binary("integer", "1", "integer", "0", "div");
        assertEquals(Double.POSITIVE_INFINITY, vm.eax.read());
    }

    @Test
    public void divDouble() {
        binary("integer", "10", "double_", "4.0", "div");
        assertEquals(2.5, vm.eax.read());
    }

    @Test
    public void bitAnd() {
        binary("integer", "5", "integer", "3", "bit_and");
        assertEquals(1, vm.eax.read());
    }

    @Test
    public void bitOr() {
        binary("integer", "5", "integer", "3", "bit_or");
        assertEquals(7, vm.eax.read());
    }

    @Test
    public void bitXor() {
        binary("integer", "5", "integer", "3", "bit_xor");
        assertEquals(6, vm.eax.read());
    }

    @Test
    public void ltTrue() {
        binary("integer", "3", "integer", "5", "lt");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void ltFalse() {
        binary("integer", "5", "integer", "3", "lt");
        assertEquals(Boolean.FALSE, vm.eax.read());
    }

    @Test
    public void gtTrue() {
        binary("integer", "5", "integer", "3", "gt");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void geEqual() {
        binary("integer", "5", "integer", "5", "ge");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void leEqual() {
        binary("integer", "3", "integer", "3", "le");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void stringCompare() {
        // both strings compare lexicographically, no numeric conversion
        binary("string", "b", "string", "a", "gt");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void eq() {
        binary("integer", "5", "integer", "5", "eq");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void veq() {
        binary("string", "a", "string", "a", "veq");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void neq() {
        binary("integer", "5", "integer", "6", "neq");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void vneq() {
        binary("string", "a", "string", "b", "vneq");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void abstractEqualsNumberString() {
        // == converts: 5 == "5"
        binary("integer", "5", "string", "5", "eq");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void strictEqualsNumberString() {
        // === does not convert: 5 !== "5"
        binary("integer", "5", "string", "5", "veq");
        assertEquals(Boolean.FALSE, vm.eax.read());
    }

    @Test
    public void strictEqualsIntDouble() {
        // both are JS Numbers: 5 === 5.0
        binary("integer", "5", "double_", "5.0", "veq");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void abstractEqualsNullUndefined() {
        // null == undefined is true in ES
        runOps(new Instruction("clear_null", "%eax", null),
                new Instruction("push_eax", null, null),
                new Instruction("undefined", null, null),
                new Instruction("eq", null, null),
                new Instruction("halt", "", ""));
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void abstractEqualsBoolNumber() {
        binary("bool", "true", "integer", "1", "eq");
        assertEquals(Boolean.TRUE, vm.eax.read());
    }

    @Test
    public void stringConcatNumber() {
        // 1 + "2" == "12"
        binary("integer", "1", "string", "2", "add");
        assertEquals("12", vm.eax.read());
    }

    @Test
    public void numericAddString() {
        // "1" + 2 == "12"
        binary("string", "1", "integer", "2", "add");
        assertEquals("12", vm.eax.read());
    }

    @Test
    public void addOverflowBecomesDouble() {
        binary("integer", "2147483647", "integer", "1", "add");
        assertEquals(2147483648.0, vm.eax.read());
    }

    @Test
    public void toInt32Bitwise() {
        binary("double_", "5.9", "integer", "1", "bit_and");
        assertEquals(1, vm.eax.read());
    }

    private void incDecTest(String op, int expected) {
        runOps(() -> frame().set("x", 5),
                new Instruction("mov_eax", "x", null),
                new Instruction(op, null, null),
                new Instruction("halt", "", ""));
        assertEquals(expected, vm.eax.read());
    }

    @Test
    public void inc() {
        incDecTest("inc", 6);
    }

    @Test
    public void dec() {
        incDecTest("dec", 4);
    }

    @Test
    public void incDouble() {
        runOps(() -> frame().set("x", 1.5),
                new Instruction("mov_eax", "x", null),
                new Instruction("inc", null, null),
                new Instruction("halt", "", ""));
        assertEquals(2.5, vm.eax.read());
    }

    @Test
    public void postIncReturnsOldValueAndIncrementsRef() {
        runOps(() -> frame().set("x", 5),
                new Instruction("mov_eax", "x", null),
                new Instruction("post_inc", null, null),
                new Instruction("halt", "", ""));
        assertEquals(5, vm.eax.read());
        assertEquals(6, frame().get("x"));
    }

    @Test
    public void postDecReturnsOldValueAndDecrementsRef() {
        runOps(() -> frame().set("x", 5),
                new Instruction("mov_eax", "x", null),
                new Instruction("post_dec", null, null),
                new Instruction("halt", "", ""));
        assertEquals(5, vm.eax.read());
        assertEquals(4, frame().get("x"));
    }

    private NgsObject frame() {
        return (NgsObject) vm.env.read();
    }
}

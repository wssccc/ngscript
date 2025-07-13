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

package org.ngscript.ast;

/**
 * A binary operation. {@code OR} and {@code AND} are short-circuit evaluated
 * by the compiler, the rest are plain binary operators.
 *
 * @author wssccc
 */
public class BinaryExpr extends Expr {

    public enum Op {
        ADD, SUB, MUL, DIV, MOD, EXP,
        OR, AND, NULLISH,
        EQ, NEQ, VEQ, VNEQ,
        LT, GT, LE, GE, INSTANCEOF, IN,
        SHL, SHR, USHR,
        BIT_OR, BIT_XOR, BIT_AND
    }

    public final Op op;
    public final Expr left;
    public final Expr right;

    public BinaryExpr(Op op, Expr left, Expr right, int line) {
        this.op = op;
        this.left = left;
        this.right = right;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, "op=" + op, left, right);
    }
}

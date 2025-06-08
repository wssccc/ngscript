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

package org.ngscript.parser.ast;

/**
 * A unary operation: negation, not, typeof, pre/post increment and decrement.
 *
 * @author wssccc
 */
public class UnaryExpr extends Expr {

    public enum Op { NEG, NOT, TYPEOF, INC, DEC, POST_INC, POST_DEC }

    public final Op op;
    public final Expr operand;

    public UnaryExpr(Op op, Expr operand, int line) {
        this.op = op;
        this.operand = operand;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, "op=" + op, operand);
    }
}

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
 * An assignment: {@code target = value}.
 *
 * @author wssccc
 */
public class AssignExpr extends Expr {

    /**
     * The assignment operator token: {@code "assign"} for plain {@code =},
     * otherwise one of the compound operators (asgn_add, asgn_sub, ...).
     */
    public final String op;
    public final Expr target;
    public final Expr value;

    /**
     * Whether the left-hand side is a parenthesized expression
     * ({@code (x) = v}): ES does not infer a name for the anonymous
     * function/class on the right in that case (SetFunctionName only
     * applies to non-cover NamedEvaluation targets).
     */
    public final boolean lhsCovered;

    public AssignExpr(Expr target, Expr value, int line) {
        this(target, value, line, "assign");
    }

    public AssignExpr(Expr target, Expr value, int line, String op) {
        this(target, value, line, op, false);
    }

    public AssignExpr(Expr target, Expr value, int line, String op, boolean lhsCovered) {
        this.op = op;
        this.target = target;
        this.value = value;
        this.lhsCovered = lhsCovered;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, "op=" + op, target, value);
    }
}

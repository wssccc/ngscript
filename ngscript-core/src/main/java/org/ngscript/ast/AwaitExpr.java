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
 * An {@code await} expression: {@code await expr}. Valid only inside an
 * async function body (the parser enforces this with the async depth).
 * At runtime the operand is evaluated, a pending promise suspends the
 * async body (the frame is saved and resumed by a microtask), and the
 * awaited value becomes the expression's value.
 *
 * @author wssccc
 */
public class AwaitExpr extends Expr {

    public final Expr operand;

    public AwaitExpr(Expr operand, int line) {
        this.operand = operand;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return "AwaitExpr[" + line + "]\n"
                + operand.toString(indent + 1);
    }
}

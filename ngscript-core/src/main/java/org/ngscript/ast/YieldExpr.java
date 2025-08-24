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
 * A yield expression inside a generator function: {@code yield expr},
 * {@code yield} (yields undefined) or {@code yield* iterable} (delegation).
 *
 * @author wssccc
 */
public class YieldExpr extends Expr {

    public final Expr operand;
    public final boolean delegate;

    public YieldExpr(Expr operand, boolean delegate, int line) {
        this.operand = operand;
        this.delegate = delegate;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, operand);
    }
}

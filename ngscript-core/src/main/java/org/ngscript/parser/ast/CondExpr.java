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
 * A conditional expression: {@code condition ? thenExpr : elseExpr}.
 *
 * @author wssccc
 */
public class CondExpr extends Expr {

    public final Expr condition;
    public final Expr thenExpr;
    public final Expr elseExpr;

    public CondExpr(Expr condition, Expr thenExpr, Expr elseExpr, int line) {
        this.condition = condition;
        this.thenExpr = thenExpr;
        this.elseExpr = elseExpr;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, condition, thenExpr, elseExpr);
    }
}
